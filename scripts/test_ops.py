"""Failure-path tests for host backups. Real PostgreSQL/TLS round-trip runs in CI separately."""

import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import ops


def configuration():
    password = "fixture-exclusive-password-123456789"
    return {
        "services": {
            "db": {
                "environment": {
                    "POSTGRES_DB": "fintry",
                    "POSTGRES_USER": "fintry",
                    "POSTGRES_PASSWORD": password,
                }
            },
            "app": {
                "environment": {
                    "APP_BASE_URL": "https://fintry.integration.test",
                    "SPRING_PROFILES_ACTIVE": "prod",
                    "DB_URL": "jdbc:postgresql://db:5432/fintry",
                    "DB_PASSWORD": password,
                    "MAIL_ENABLED": "true",
                    "SMTP_AUTH": "true",
                    "MAIL_FROM": "noreply@fintry.integration.test",
                    "SMTP_HOST": "relay",
                    "SMTP_USERNAME": "fixture",
                    "SMTP_PASSWORD": "fixture-only",
                    "SMTP_STARTTLS": "true",
                    "SMTP_SSL": "false",
                }
            },
            "proxy": {"environment": {"FINTRY_DOMAIN": "fintry.integration.test"}},
        }
    }


class FakeCompose:
    def __init__(self, fail=None):
        self.config = configuration()
        self.calls = []
        self.fail = fail

    def check_database_target(self):
        self.calls.append("target-checked")

    def exec(self, service, args, *, stdin=None, stdout=None, timeout=None):
        cmd = " ".join(args)
        self.calls.append(cmd)
        if self.fail and self.fail in cmd:
            raise ops.OpsError("fixture failure")
        if "pg_dump -U" in cmd:
            stdout.write(b"PGDMP-fintry-fixture\x00\xff")
        if "--version" in cmd:
            return b"pg_dump (PostgreSQL) 17.7\n"
        if "psql" in cmd:
            return b'{"companies":1,"entries":2,"attachments":1,"schemaVersion":"12"}'
        return b""


class BackupTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name)
        self.previous_mask = os.umask(0o077)

    def tearDown(self):
        os.umask(self.previous_mask)
        self.directory.cleanup()

    def archive(self):
        return Path(ops.backup(FakeCompose(), self.path)["backup"])

    def test_publishes_private_pair_with_checksum_but_does_not_claim_restore(self):
        result = ops.backup(FakeCompose(), self.path)
        path, info = ops.archive_path(result["backup"])
        self.assertEqual(info["sha256"], ops.digest(path))
        self.assertEqual(info["bytes"], path.stat().st_size)
        self.assertFalse(result["restorationTested"])
        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(Path(result["manifest"]).stat().st_mode), 0o600)
        self.assertFalse(list(self.path.glob("*.part")))

    def test_dump_failure_keeps_existing_backups_and_does_not_publish_partial(self):
        original = self.archive()
        original_bytes = original.read_bytes()
        with self.assertRaises(ops.OpsError):
            ops.backup(FakeCompose("pg_dump -"), self.path)
        self.assertEqual([original], list(self.path.glob("*.dump")))
        self.assertEqual(original_bytes, original.read_bytes())
        self.assertFalse(list(self.path.glob("*.part")))

    def test_corrupt_or_missing_manifest_refuses_restore_before_database_creation(self):
        path = self.archive()
        path.write_bytes(path.read_bytes() + b"corruption")
        compose = FakeCompose()
        with self.assertRaises(ops.OpsError):
            ops.restore(compose, path, keep=True)
        self.assertEqual([], compose.calls)
        path.with_suffix(".dump.json").write_text("[]")
        with self.assertRaises(ops.OpsError):
            ops.archive_path(path)
        path.with_suffix(".dump.json").unlink()
        with self.assertRaises(ops.OpsError):
            ops.archive_path(path)

    def test_failed_restore_keeps_isolated_database_and_never_drops_production(self):
        path = self.archive()
        compose = FakeCompose("--single-transaction")
        with self.assertRaisesRegex(ops.OpsError, "fintry_restore_"):
            ops.restore(compose, path, keep=True)
        commands = "\n".join(compose.calls)
        self.assertIn("createdb", commands)
        self.assertNotIn("dropdb", commands)
        self.assertNotIn("--create ", commands)
        self.assertIn('"$1"', commands)

    def test_restore_keeps_new_database_and_drill_removes_only_its_own_database(self):
        path = self.archive()
        for keep in [True, False]:
            compose = FakeCompose()
            result = ops.restore(compose, path, keep=keep)
            self.assertTrue(result["restored"])
            self.assertEqual(2, result["counts"]["entries"])
            created = next(c for c in compose.calls if "createdb" in c).split()[-1]
            self.assertRegex(created, r"^fintry_restore_[a-z0-9_]+$")
            drops = [c for c in compose.calls if "dropdb" in c]
            self.assertEqual(0 if keep else 1, len(drops))
            if drops:
                self.assertEqual(created, drops[0].split()[-1])
            self.assertEqual(keep, result["database"] is not None)

    def test_command_failure_and_timeout_do_not_echo_passwords_or_database_data(self):
        for response in [
            subprocess.CompletedProcess([], 1, b"sensitive-data", b"password-secret"),
            subprocess.TimeoutExpired("password-secret", 1),
        ]:
            with patch(
                "ops.subprocess.run",
                **(
                    {"side_effect": response}
                    if isinstance(response, Exception)
                    else {"return_value": response}
                )
            ):
                with self.assertRaises(ops.OpsError) as caught:
                    ops.run(["docker", "compose"])
                self.assertNotIn("secret", str(caught.exception))
                self.assertNotIn("sensitive", str(caught.exception))

    def test_configuration_rejects_exposed_database_wrong_url_and_insecure_mail(self):
        ops.validate_configuration(configuration())
        bad = []
        a = configuration()
        a["services"]["db"]["ports"] = [{"published": "5432"}]
        bad.append(a)
        a = configuration()
        a["services"]["app"]["environment"][
            "APP_BASE_URL"
        ] = "http://fintry.integration.test"
        bad.append(a)
        a = configuration()
        a["services"]["app"]["environment"]["SMTP_SSL"] = "true"
        bad.append(a)
        a = configuration()
        a["services"]["proxy"]["environment"][
            "FINTRY_DOMAIN"
        ] = "example.test\nrespond 200"
        bad.append(a)
        for config in bad:
            with self.assertRaises(ops.OpsError):
                ops.validate_configuration(config)

    def test_health_requires_https_and_rejects_redirects(self):
        for url in [
            "http://example.test/healthz",
            "https://user:password@example.test/healthz",
            "https://example.test/healthz?token=secret",
        ]:
            with self.assertRaises(ops.OpsError):
                ops.health(url)
        self.assertIsNone(
            ops.NoRedirect().redirect_request(
                None, None, 302, "", {}, "http://example.test/healthz"
            )
        )


if __name__ == "__main__":
    unittest.main()
