#!/usr/bin/env python3
"""Fintry host operations. Python 3.10+, Docker Compose; no dotenv sourcing or shell eval."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
from datetime import datetime, timezone
from urllib.parse import urlsplit
from urllib.request import build_opener, HTTPRedirectHandler

ROOT = Path(__file__).resolve().parents[1]
FORMAT = "fintry-postgresql-custom-v1"


class OpsError(Exception):
    pass


def run(command, *, stdin=None, stdout=subprocess.PIPE, timeout=120):
    try:
        result = subprocess.run(
            command, stdin=stdin, stdout=stdout, stderr=subprocess.PIPE, timeout=timeout
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise OpsError(
            "Não foi possível executar a operação. Confira Docker/Compose e a disponibilidade do serviço."
        ) from exc
    if result.returncode:
        # Docker/SQL errors may echo environment values or data. Keep operator output credential-free.
        raise OpsError(
            "A operação falhou. A saída técnica foi omitida para preservar credenciais e dados; confira o estado dos serviços e os logs no servidor."
        )
    return result.stdout or b""


class Compose:
    def __init__(self, env_file=None, overlays=(), project=None, local=False):
        default = ROOT / (".env" if local else "deploy/production.env")
        env_file = Path(env_file or default).resolve()
        if not env_file.is_file() and (not local or env_file != default.resolve()):
            raise OpsError(
                "Arquivo de configuração não encontrado. Copie deploy/production.env.example e preencha os campos."
            )
        self.command = ["docker", "compose"]
        if env_file.is_file():
            self.command += ["--env-file", str(env_file)]
        self.command += [
            "-f",
            str(
                ROOT
                / ("docker-compose.yml" if local else "deploy/compose.production.yml")
            ),
        ]
        for overlay in overlays:
            self.command += ["-f", str(Path(overlay).resolve())]
        if project:
            if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,62}", project):
                raise OpsError("Nome de projeto Compose inválido.")
            self.command += ["--project-name", project]
        try:
            self.config = json.loads(run(self.command + ["config", "--format", "json"]))
        except (ValueError, KeyError) as exc:
            raise OpsError("Configuração Compose inválida.") from exc
        if local:
            try:
                if (
                    self.config["services"]["db"]["environment"]["POSTGRES_USER"]
                    != "fintry"
                ):
                    raise OpsError("Usuário do banco local inesperado.")
            except (KeyError, TypeError) as exc:
                raise OpsError(
                    "A composição local precisa conter o banco PostgreSQL do Fintry."
                ) from exc
        else:
            validate_configuration(self.config)

    def exec(self, service, args, **kwargs):
        return run(self.command + ["exec", "-T", service] + list(args), **kwargs)

    def check_database_target(self):
        actual = (
            self.exec(
                "db",
                ["sh", "-c", 'printf "%s\\n%s\\n" "$POSTGRES_DB" "$POSTGRES_USER"'],
            )
            .decode()
            .splitlines()
        )
        expected = self.config["services"]["db"]["environment"]
        if actual != [expected["POSTGRES_DB"], expected["POSTGRES_USER"]]:
            raise OpsError(
                "O container do banco difere do arquivo de configuração. Confira o projeto/ambiente antes de continuar."
            )


def validate_configuration(config):
    try:
        services = config["services"]
        app = services["app"]["environment"]
        db = services["db"]["environment"]
        proxy = services["proxy"]["environment"]
        domain = proxy["FINTRY_DOMAIN"]
        if not re.fullmatch(
            r"(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}",
            domain,
        ):
            raise OpsError(
                "FINTRY_DOMAIN deve ser um domínio DNS sem protocolo, porta ou caminho."
            )
        if app["APP_BASE_URL"] != "https://" + domain:
            raise OpsError("A URL pública deve coincidir com o domínio HTTPS do proxy.")
        if (
            app["SPRING_PROFILES_ACTIVE"] != "prod"
            or app["DB_URL"] != "jdbc:postgresql://db:5432/" + db["POSTGRES_DB"]
        ):
            raise OpsError(
                "O perfil ou a conexão PostgreSQL de produção está inconsistente."
            )
        if (
            not re.fullmatch(r"[a-z][a-z0-9_]{0,62}", db["POSTGRES_DB"])
            or db["POSTGRES_USER"] != "fintry"
        ):
            raise OpsError("Use um nome de banco simples e o usuário fintry.")
        if (
            len(db["POSTGRES_PASSWORD"]) < 20
            or db["POSTGRES_PASSWORD"] != app["DB_PASSWORD"]
            or "change_me" in db["POSTGRES_PASSWORD"].lower()
        ):
            raise OpsError(
                "Configure a mesma senha exclusiva de banco, com pelo menos 20 caracteres."
            )
        if services["app"].get("ports") or services["db"].get("ports"):
            raise OpsError(
                "App e PostgreSQL devem permanecer sem portas publicadas em produção."
            )
        if (
            str(app["MAIL_ENABLED"]).lower() != "true"
            or str(app["SMTP_AUTH"]).lower() != "true"
        ):
            raise OpsError("Habilite o envio de e-mail com autenticação SMTP.")
        if any(
            not str(app[k]).strip()
            for k in ["MAIL_FROM", "SMTP_HOST", "SMTP_USERNAME", "SMTP_PASSWORD"]
        ):
            raise OpsError("Preencha remetente, servidor e autenticação SMTP.")
        if app["SMTP_HOST"].lower() in {"localhost", "127.0.0.1", "mailpit"} or app[
            "MAIL_FROM"
        ].endswith(".local"):
            raise OpsError(
                "Substitua o e-mail de desenvolvimento pela configuração de produção."
            )
        if str(app["SMTP_STARTTLS"]).lower() == str(app["SMTP_SSL"]).lower():
            raise OpsError(
                "Escolha STARTTLS ou TLS implícito para SMTP, sem habilitar ambos."
            )
    except (KeyError, TypeError) as exc:
        raise OpsError(
            "A configuração está incompleta; use o modelo de produção e confira os campos obrigatórios."
        ) from exc


def digest(path):
    sha = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(block)
    return sha.hexdigest()


def archive_path(path):
    path = Path(path).resolve()
    if not path.is_file() or path.suffix != ".dump":
        raise OpsError(
            "Informe um arquivo .dump criado pela rotina de backup do Fintry."
        )
    try:
        info = json.loads(path.with_suffix(".dump.json").read_text())
    except (OSError, ValueError) as exc:
        raise OpsError(
            "Manifesto do backup ausente ou inválido. O arquivo não será restaurado."
        ) from exc
    if (
        not isinstance(info, dict)
        or info.get("format") != FORMAT
        or info.get("bytes") != path.stat().st_size
        or not secrets.compare_digest(str(info.get("sha256", "")), digest(path))
    ):
        raise OpsError(
            "O tamanho ou SHA-256 do backup não corresponde ao manifesto. O arquivo não será restaurado."
        )
    return path, info


def check_archive(compose, path):
    path, info = archive_path(path)
    with path.open("rb") as stream:
        compose.exec(
            "db",
            ["pg_restore", "--list"],
            stdin=stream,
            stdout=subprocess.DEVNULL,
            timeout=300,
        )
    return path, info


def backup(compose, output):
    compose.check_database_target()
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = output / f"fintry-{stamp}-{secrets.token_hex(4)}.dump"
    temporary = path.with_suffix(".dump.part")
    manifest = path.with_suffix(".dump.json")
    manifest_part = manifest.with_suffix(".json.part")
    try:
        with temporary.open("xb") as stream:
            compose.exec(
                "db",
                [
                    "sh",
                    "-c",
                    'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges',
                ],
                stdout=stream,
                timeout=3600,
            )
            stream.flush()
            os.fsync(stream.fileno())
        with temporary.open("rb") as stream:
            compose.exec(
                "db",
                ["pg_restore", "--list"],
                stdin=stream,
                stdout=subprocess.DEVNULL,
                timeout=300,
            )
        info = {
            "format": FORMAT,
            "createdAt": datetime.now(timezone.utc).isoformat(),
            "sha256": digest(temporary),
            "bytes": temporary.stat().st_size,
            "postgresVersion": compose.exec("db", ["pg_dump", "--version"])
            .decode()
            .strip(),
        }
        with manifest_part.open("x", encoding="utf-8") as stream:
            json.dump(info, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        temporary.rename(path)
        manifest_part.rename(manifest)
        return {
            "backup": str(path),
            "manifest": str(manifest),
            "bytes": info["bytes"],
            "sha256": info["sha256"],
            "restorationTested": False,
        }
    finally:
        temporary.unlink(missing_ok=True)
        manifest_part.unlink(missing_ok=True)


def restore(compose, path, *, keep):
    # Both verification and real recovery always create a new database; never --create from a dump.
    path, info = check_archive(compose, path)
    compose.check_database_target()
    database = (
        "fintry_restore_"
        + datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S_")
        + secrets.token_hex(4)
    )
    compose.exec(
        "db",
        [
            "sh",
            "-c",
            'exec createdb -U "$POSTGRES_USER" --owner="$POSTGRES_USER" --template=template0 "$1"',
            "ops",
            database,
        ],
    )
    start = time.monotonic()
    try:
        with path.open("rb") as stream:
            compose.exec(
                "db",
                [
                    "sh",
                    "-c",
                    'exec pg_restore -U "$POSTGRES_USER" --dbname="$1" --clean --if-exists --no-owner --no-privileges --single-transaction --exit-on-error',
                    "ops",
                    database,
                ],
                stdin=stream,
                stdout=subprocess.DEVNULL,
                timeout=3600,
            )
        query = "SELECT json_build_object('companies',(SELECT count(*) FROM companies),'entries',(SELECT count(*) FROM financial_entries),'attachments',(SELECT count(*) FROM entry_attachments),'schemaVersion',(SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1))"
        report = json.loads(
            compose.exec(
                "db",
                [
                    "sh",
                    "-c",
                    'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -At -c "$2"',
                    "ops",
                    database,
                    query,
                ],
            ).decode()
        )
        if not report.get("schemaVersion"):
            raise OpsError(
                "A restauração não contém o histórico de migrações esperado."
            )
        if not keep:
            compose.exec(
                "db",
                ["sh", "-c", 'exec dropdb -U "$POSTGRES_USER" "$1"', "ops", database],
            )
        return {
            "restored": True,
            "database": database if keep else None,
            "isolatedDatabaseRemoved": not keep,
            "seconds": round(time.monotonic() - start, 2),
            "counts": report,
            "sha256": info["sha256"],
        }
    except (OpsError, ValueError) as exc:
        raise OpsError(
            f"A restauração falhou. Confira a base isolada {database} para análise; nenhum banco existente foi substituído."
        ) from exc


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def health(url):
    parsed = urlsplit(url)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
    ):
        raise OpsError(
            "O monitoramento externo exige URL HTTPS sem credenciais, query ou fragmento."
        )
    try:
        with build_opener(NoRedirect()).open(url, timeout=10) as response:
            status = json.loads(response.read(16384))
            if response.status != 200 or status.get("status") != "UP":
                raise OpsError("O ambiente não está pronto para atender.")
    except (OSError, ValueError) as exc:
        raise OpsError(
            "A verificação HTTPS falhou. Confira certificado, proxy, aplicação e banco de dados."
        ) from exc
    return {"status": "UP", "checkedAt": datetime.now(timezone.utc).isoformat()}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--env-file",
        type=Path,
        help="Padrão: deploy/production.env, ou .env no modo local",
    )
    parser.add_argument(
        "--local",
        action="store_true",
        help="Operar somente o PostgreSQL da composição local, em vez da produção",
    )
    parser.add_argument(
        "--overlay",
        type=Path,
        action="append",
        default=[],
        help="Compose adicional; reservado para ambientes controlados de teste",
    )
    parser.add_argument(
        "--project-name", help="Projeto Compose isolado, por exemplo para CI"
    )
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser(
        "check",
        help="Validar configuração, sem iniciar serviços nem mostrar credenciais",
    )
    b = sub.add_parser("backup", help="Criar arquivo PostgreSQL e manifesto SHA-256")
    b.add_argument("--output", type=Path, default=ROOT / "backups")
    for name in ["verify", "restore", "restore-drill"]:
        p = sub.add_parser(
            name,
            help="Verificar arquivo, restaurar em nova base ou testar restauração isolada",
        )
        p.add_argument("archive", type=Path)
    h = sub.add_parser(
        "health", help="Verificar prontidão por HTTPS, com certificado validado"
    )
    h.add_argument("--url", help="Padrão: URL pública configurada + /healthz")
    args = parser.parse_args(argv)
    os.umask(0o077)
    try:
        compose = Compose(
            args.env_file, args.overlay, args.project_name, local=args.local
        )
        if args.action == "check":
            result = {"configuration": "valid", "production": not args.local}
        elif args.action == "backup":
            result = backup(compose, args.output)
        elif args.action == "verify":
            path, info = check_archive(compose, args.archive)
            result = {
                "verified": True,
                "backup": str(path),
                "sha256": info["sha256"],
                "restorationTested": False,
            }
        elif args.action in {"restore", "restore-drill"}:
            result = restore(compose, args.archive, keep=args.action == "restore")
        else:
            result = health(
                args.url
                or compose.config["services"]["app"]["environment"]["APP_BASE_URL"]
                + "/healthz"
            )
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except OpsError as exc:
        print("Erro: " + str(exc), file=sys.stderr)
        return 1
    except OSError:
        print(
            "Erro: não foi possível acessar/gravar os arquivos. Confira permissões e espaço em disco.",
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
