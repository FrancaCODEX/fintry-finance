#!/usr/bin/env python3
"""Operator-only manual activation; no public activation endpoint or payment provider calls."""
from __future__ import annotations

import argparse
import json
import re
import tempfile
import uuid

import ops


def request_id(value):
    try:
        return str(uuid.UUID(value))
    except ValueError as exc:
        raise ops.OpsError("Identificador de solicitação inválido.") from exc


def activation_sql(identifier, days, reference):
    identifier = request_id(identifier)
    if not isinstance(days, int) or not 1 <= days <= 366:
        raise ops.OpsError("Use um período de 1 a 366 dias.")
    if not re.fullmatch(r"[A-Za-z0-9_./:-]{1,120}", reference):
        raise ops.OpsError("Referência inválida: use letras/números, _, ., /, : ou - (até 120).")
    event = str(uuid.uuid4())
    # Every interpolated value is a normalized UUID, bounded integer or validated ASCII reference.
    return f"""BEGIN;
SELECT id FROM companies WHERE id=(SELECT company_id FROM saas_plan_requests WHERE id='{identifier}') FOR UPDATE;
DO $activation$
DECLARE r saas_plan_requests%ROWTYPE; s saas_subscriptions%ROWTYPE; p saas_plans%ROWTYPE; deadline TIMESTAMPTZ;
BEGIN
 SELECT * INTO r FROM saas_plan_requests WHERE id='{identifier}';
 IF NOT FOUND THEN RAISE EXCEPTION 'Unknown request'; END IF;
 IF r.status='APPROVED' AND r.reference='{reference}' AND r.granted_days={days} THEN RETURN; END IF;
 IF EXISTS(SELECT 1 FROM saas_invoices WHERE request_id=r.id AND status<>'CANCELLED') THEN RAISE EXCEPTION 'Request has an integrated invoice; use payment reconciliation'; END IF;
 IF r.status<>'PENDING' THEN RAISE EXCEPTION 'Request already resolved'; END IF;
 SELECT * INTO s FROM saas_subscriptions WHERE company_id=r.company_id;
 IF s.pending_request_id IS DISTINCT FROM r.id THEN RAISE EXCEPTION 'Stale request'; END IF;
 SELECT * INTO p FROM saas_plans WHERE code=r.plan;
 IF (SELECT count(*) FROM app_users WHERE company_id=r.company_id AND (active OR invited))>p.member_limit
 OR (SELECT count(*) FROM financial_accounts WHERE company_id=r.company_id)>p.account_limit
 OR (SELECT coalesce(sum(byte_count),0) FROM entry_attachments WHERE company_id=r.company_id)>p.storage_bytes
 THEN RAISE EXCEPTION 'Usage exceeds selected plan'; END IF;
 deadline:=GREATEST(CURRENT_TIMESTAMP,coalesce(s.paid_until,CURRENT_TIMESTAMP))+make_interval(days=>{days});
 UPDATE saas_subscriptions SET plan=r.plan,state='ACTIVE',paid_until=deadline,cancel_at_period_end=FALSE,
 pending_request_id=NULL,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE company_id=r.company_id;
 UPDATE saas_plan_requests SET status='APPROVED',reference='{reference}',granted_days={days},active_until=deadline,
 updated_at=CURRENT_TIMESTAMP WHERE id=r.id;
 INSERT INTO audit_events(id,company_id,actor_name,action,resource,resource_id,label,changes_json,occurred_at)
 VALUES('{event}',r.company_id,'Operação comercial','SUBSCRIPTION_ACTIVATE','SUBSCRIPTION',r.id,
 'Ativação manual · {reference}',jsonb_build_array(
 jsonb_build_object('field','Plano','before',s.plan,'after',r.plan),
 jsonb_build_object('field','Acesso até','before',coalesce(s.paid_until::text,''),'after',deadline::text))::text,CURRENT_TIMESTAMP);
END; $activation$;
COMMIT;
"""


def sql(compose, text):
    compose.check_database_target()
    target = compose.config["services"]["db"]["environment"]
    with tempfile.TemporaryFile() as source:
        source.write(text.encode())
        source.seek(0)
        return compose.exec(
            "db", ["psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", target["POSTGRES_USER"], "-d", target["POSTGRES_DB"]], stdin=source
        ).decode().strip()


def inspect(compose, identifier):
    identifier = request_id(identifier)
    raw = sql(compose, f"""SELECT row_to_json(v) FROM (
SELECT r.id,r.company_id,r.plan,r.status,r.active_until,r.granted_days,s.pending_request_id,s.paid_until,
 (SELECT count(*) FROM app_users WHERE company_id=r.company_id AND (active OR invited)) AS members,
 (SELECT count(*) FROM financial_accounts WHERE company_id=r.company_id) AS accounts,
 (SELECT coalesce(sum(byte_count),0) FROM entry_attachments WHERE company_id=r.company_id) AS storage_bytes,
 p.member_limit,p.account_limit,p.storage_bytes AS storage_limit
FROM saas_plan_requests r JOIN saas_subscriptions s ON s.company_id=r.company_id JOIN saas_plans p ON p.code=r.plan
WHERE r.id='{identifier}') v;""")
    if not raw:
        raise ops.OpsError("Solicitação não encontrada.")
    return json.loads(raw)


def main():
    parser = argparse.ArgumentParser(description="Inspecionar ou ativar manualmente um plano após conferência comercial.")
    parser.add_argument("--env-file")
    parser.add_argument("--overlay", action="append", default=[])
    parser.add_argument("--project")
    parser.add_argument("--local", action="store_true")
    parser.add_argument("--request", required=True)
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--reference", help="Referência interna da conferência; não use dados pessoais ou credenciais.")
    parser.add_argument("--apply", action="store_true", help="Ativa o período; sem esta opção apenas inspeciona a solicitação.")
    args = parser.parse_args()
    try:
        identifier = request_id(args.request)
        statement = activation_sql(identifier, args.days, args.reference or "preview")
        compose = ops.Compose(args.env_file, args.overlay, args.project, args.local)
        preview = inspect(compose, identifier)
        if args.apply:
            if not args.reference:
                raise ops.OpsError("Informe --reference após conferir a autorização comercial/pagamento.")
            sql(compose, statement)
            preview = inspect(compose, identifier)
        print(json.dumps({"applied": args.apply, "request": preview}, ensure_ascii=False, indent=2))
    except (ops.OpsError, ValueError) as exc:
        parser.exit(1, str(exc) + "\n")


if __name__ == "__main__":
    main()
