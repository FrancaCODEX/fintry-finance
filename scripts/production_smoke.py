#!/usr/bin/env python3
"""CI-only real Docker/TLS/SMTP/backup drill. Creates and removes its own isolated project."""

import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time

import ops


def command(args, **kwargs):
    return ops.run(args, **kwargs)


def main():
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix="fintry-operations-") as directory:
        temp = Path(directory)
        # These are disposable certificates and credentials, used only inside the CI network.
        command(
            [
                "openssl",
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-nodes",
                "-days",
                "1",
                "-subj",
                "/CN=Fintry-CI-Mail",
                "-addext",
                "subjectAltName=DNS:relay",
                "-keyout",
                str(temp / "relay.key"),
                "-out",
                str(temp / "relay.crt"),
            ]
        )
        command(
            [
                "keytool",
                "-importcert",
                "-noprompt",
                "-alias",
                "ci-mail",
                "-file",
                str(temp / "relay.crt"),
                "-keystore",
                str(temp / "trust.p12"),
                "-storetype",
                "PKCS12",
                "-storepass",
                "fixture-only",
            ]
        )
        # Non-root app needs read-only access to this public trust store.
        (temp / "trust.p12").chmod(0o644)
        (temp / "relay.crt").chmod(0o644)
        (temp / "relay.key").chmod(0o644)
        domain = "fintry.integration.test"
        env = temp / "production.env"
        env.write_text(
            "\n".join(
                [
                    f"FINTRY_DOMAIN={domain}",
                    "ACME_EMAIL=ops@fintry.integration.test",
                    "DB_PASSWORD=" + secrets.token_hex(24),
                    "DB_NAME=fintry",
                    "MAIL_FROM=noreply@fintry.integration.test",
                    "SMTP_HOST=relay",
                    "SMTP_PORT=1025",
                    "SMTP_USERNAME=fixture",
                    "SMTP_PASSWORD=fixture-only",
                    "SMTP_STARTTLS=true",
                    "SMTP_SSL=false",
                    "FINTRY_IMAGE=fintry-ci",
                    "",
                ]
            )
        )
        caddy = temp / "Caddyfile"
        # Exercise the production routing/header rules with an internal CA, without public ACME requests.
        caddy.write_text(
            (ops.ROOT / "deploy/Caddyfile")
            .read_text()
            .replace("\tencode zstd gzip", "\ttls internal\n\tencode zstd gzip")
        )
        overlay = temp / "test.yml"
        overlay.write_text(
            json.dumps(
                {
                    "services": {
                        "proxy": {"volumes": [f"{caddy}:/etc/caddy/Caddyfile:ro"]},
                        "app": {
                            "environment": {
                                "JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=75.0 -Duser.home=/tmp -Djavax.net.ssl.trustStore=/run/mail-trust.p12 -Djavax.net.ssl.trustStorePassword=fixture-only"
                            },
                            "volumes": [f"{temp / 'trust.p12'}:/run/mail-trust.p12:ro"],
                        },
                        "relay": {
                            "image": "axllent/mailpit:v1.31.6",
                            "networks": ["web"],
                            "volumes": [
                                f"{temp / 'relay.crt'}:/tls/cert.pem:ro",
                                f"{temp / 'relay.key'}:/tls/key.pem:ro",
                            ],
                            "environment": {
                                "MP_SMTP_TLS_CERT": "/tls/cert.pem",
                                "MP_SMTP_TLS_KEY": "/tls/key.pem",
                                "MP_SMTP_REQUIRE_STARTTLS": "true",
                                "MP_SMTP_AUTH": "fixture:fixture-only",
                            },
                        },
                    }
                }
            )
        )
        project = "fintry-ci-" + secrets.token_hex(4)
        compose = ops.Compose(env, [overlay], project)
        base = compose.command
        try:
            # Validate the unmodified production Caddyfile before starting the local-CA variant.
            command(
                base
                + [
                    "run",
                    "--rm",
                    "--no-deps",
                    "-v",
                    str(ops.ROOT / "deploy/Caddyfile") + ":/etc/caddy/Caddyfile:ro",
                    "proxy",
                    "caddy",
                    "validate",
                    "--config",
                    "/etc/caddy/Caddyfile",
                    "--adapter",
                    "caddyfile",
                ],
                timeout=120,
            )
            command(
                base + ["up", "--no-build", "-d", "--wait", "--wait-timeout", "240"],
                timeout=300,
            )
            root = temp / "caddy-root.crt"
            for _ in range(30):
                try:
                    command(
                        base
                        + [
                            "cp",
                            "proxy:/data/caddy/pki/authorities/local/root.crt",
                            str(root),
                        ]
                    )
                    break
                except ops.OpsError:
                    time.sleep(1)
            if not root.exists():
                raise AssertionError("Caddy não criou a CA de teste")
            common = [
                "curl",
                "--silent",
                "--show-error",
                "--noproxy",
                "*",
                "--max-time",
                "20",
                "--cacert",
                str(root),
                "--resolve",
                domain + ":443:127.0.0.1",
            ]
            jar = temp / "cookies.txt"
            headers = temp / "headers.txt"
            token = {}

            def request(method, path, body=None, expected=200, extra=(), session=True):
                args = common + [
                    "-D",
                    str(headers),
                    "-w",
                    "\n%{http_code}",
                    "-X",
                    method,
                ]
                if session:
                    args += ["-b", str(jar), "-c", str(jar)]
                if token and method != "GET":
                    args += ["-H", token["headerName"] + ": " + token["token"]]
                if body is not None:
                    args += [
                        "-H",
                        "Content-Type: application/json",
                        "--data-binary",
                        "@-",
                    ]
                for header in extra:
                    args += ["-H", header]
                args += ["https://" + domain + path]
                stream = tempfile.TemporaryFile()
                try:
                    if body is not None:
                        stream.write(json.dumps(body).encode())
                        stream.seek(0)
                    result = command(
                        args, stdin=stream if body is not None else None
                    ).decode()
                finally:
                    stream.close()
                payload, code = result.rsplit("\n", 1)
                if int(code) != expected:
                    raise AssertionError(
                        f"HTTP inesperado em {path}: {code}, esperado {expected}"
                    )
                return (
                    json.loads(payload)
                    if payload.strip().startswith(("{", "["))
                    else payload
                )

            for _ in range(40):
                try:
                    assert request("GET", "/healthz", session=False) == {"status": "UP"}
                    break
                except (ops.OpsError, AssertionError):
                    time.sleep(1)
            else:
                raise AssertionError("Prontidão HTTPS não ficou disponível")
            request("GET", "/actuator/health/readiness", expected=404, session=False)
            token = request("GET", "/api/v1/auth/csrf")
            account = {
                "name": "Pessoa CI",
                "companyName": "Fintry CI",
                "document": "12345678000190",
                "email": "admin@fintry.integration.test",
                "password": "Senha-fixture-123",
            }
            request("POST", "/api/v1/auth/register", account, 201)
            request(
                "POST",
                "/api/v1/auth/login",
                {"email": account["email"], "password": account["password"]},
            )
            token = request(
                "GET",
                "/api/v1/auth/csrf",
                extra=[
                    "Forwarded: proto=http;host=attacker.invalid",
                    "X-Forwarded-Proto: http",
                ],
            )
            assert "strict-transport-security:" in headers.read_text().lower()
            # Verify Secure at registration/login response through the HTTPS boundary.
            assert "TRUE\t" in jar.read_text()  # Netscape cookie secure flag.
            status = request("GET", "/api/v1/operations")
            assert status["production"] and status["secureCookies"] and status["ready"]
            assert status["mailMode"] == "SMTP"
            request("POST", "/api/v1/operations/mail-test", {}, 202)
            messages = json.loads(
                compose.exec(
                    "app",
                    ["curl", "--silent", "--fail", "http://relay:8025/api/v1/messages"],
                )
            )
            assert messages["total"] == 1
            assert messages["messages"][0]["To"][0]["Address"] == account["email"]
            request(
                "POST", "/api/v1/auth/forgot-password", {"email": account["email"]}, 202
            )
            for _ in range(30):
                messages = json.loads(
                    compose.exec(
                        "app",
                        [
                            "curl",
                            "--silent",
                            "--fail",
                            "http://relay:8025/api/v1/messages",
                        ],
                    )
                )
                if messages["total"] >= 2:
                    break
                time.sleep(0.2)
            assert messages["total"] == 2
            # Add demo business data, including PDFs in bytea, before the backup round-trip.
            request("POST", "/api/v1/finance/sample", {}, 201)
            query = "SELECT json_build_object('companies',(SELECT count(*) FROM companies),'entries',(SELECT count(*) FROM financial_entries),'attachments',(SELECT count(*) FROM entry_attachments),'attachmentsHash',(SELECT md5(coalesce(string_agg(md5(encode(content,'hex')),',' ORDER BY id),'')) FROM entry_attachments),'entriesHash',(SELECT md5(coalesce(string_agg(id::text||amount::text||status,',' ORDER BY id),'')) FROM financial_entries),'schema',(SELECT count(*) FROM flyway_schema_history WHERE success))"

            def snapshot(database):
                return (
                    compose.exec(
                        "db",
                        [
                            "psql",
                            "-X",
                            "-U",
                            "fintry",
                            "-d",
                            database,
                            "-At",
                            "-v",
                            "ON_ERROR_STOP=1",
                            "-c",
                            query,
                        ],
                    )
                    .decode()
                    .strip()
                )

            before = snapshot("fintry")
            archive = ops.backup(compose, temp / "backups")
            result = ops.restore(compose, archive["backup"], keep=True)
            assert snapshot(result["database"]) == before
            assert snapshot("fintry") == before
            # A corrupt backup must be refused before creating any destination database.
            corrupt = temp / "bad.dump"
            corrupt.write_bytes(b"corrupted")
            corrupt.with_suffix(".dump.json").write_text(
                Path(archive["manifest"]).read_text()
            )
            try:
                ops.restore(compose, corrupt, keep=True)
                raise AssertionError("Backup corrompido foi aceito")
            except ops.OpsError:
                pass
            assert snapshot("fintry") == before
            assert ops.restore(compose, archive["backup"], keep=False)[
                "isolatedDatabaseRemoved"
            ]
            # A database outage affects readiness, while liveness stays independent even with a session.
            session_cookie = next(
                line.split("\t")[-1]
                for line in jar.read_text().splitlines()
                if "\tJSESSIONID\t" in line
            )
            command(base + ["stop", "db"], timeout=60)
            time.sleep(6)  # Health cache TTL is five seconds.
            live = json.loads(
                compose.exec(
                    "app",
                    [
                        "curl",
                        "--silent",
                        "--fail",
                        "-H",
                        "Cookie: JSESSIONID=" + session_cookie,
                        "http://localhost:8080/actuator/health/liveness",
                    ],
                )
            )
            assert live == {"status": "UP"}
            assert request("GET", "/healthz", expected=503, session=False) == {
                "status": "DOWN"
            }
            command(base + ["start", "db"], timeout=60)
            print(
                "PASS produção: HTTPS/certificado interno validado, headers, cookies Secure, SMTP STARTTLS autenticado e recuperação no capturador, PostgreSQL, backup/restauração com comprovantes íntegros, corrupção recusada, origem preservada e prontidão/liveness separados."
            )
        except Exception:
            # Only disposable CI fixtures exist in this generated project; redact even their passwords.
            try:
                logs = command(base + ["logs", "--no-color", "--tail", "100"]).decode(
                    errors="replace"
                )
                for service in compose.config["services"].values():
                    for key, value in service.get("environment", {}).items():
                        if ("PASSWORD" in key or "SECRET" in key) and value:
                            logs = logs.replace(str(value), "[redacted]")
                print(logs, file=__import__("sys").stderr)
            except ops.OpsError:
                pass
            raise
        finally:
            # This project name is generated here and can never point at fintry-prod or the local stack.
            assert project.startswith("fintry-ci-")
            command(base + ["down", "--volumes", "--remove-orphans"], timeout=120)


if __name__ == "__main__":
    main()
