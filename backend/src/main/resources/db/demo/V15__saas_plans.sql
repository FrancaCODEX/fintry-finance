CREATE TABLE saas_plans (
 code VARCHAR(12) PRIMARY KEY, name VARCHAR(80) NOT NULL, description VARCHAR(255) NOT NULL,
 member_limit INTEGER NOT NULL CHECK(member_limit>0), account_limit INTEGER NOT NULL CHECK(account_limit>0),
 storage_bytes BIGINT NOT NULL CHECK(storage_bytes>0), monthly_price NUMERIC(12,2) CHECK(monthly_price>=0)
);
INSERT INTO saas_plans(code,name,description,member_limit,account_limit,storage_bytes) VALUES
 ('ESSENTIAL','Essencial','O financeiro organizado para uma equipe enxuta.',2,3,104857600),
 ('PRO','Profissional','Mais espaço para acompanhar o crescimento da empresa.',5,10,524288000),
 ('BUSINESS','Gestão','Capacidade para equipes e operações maiores.',15,30,2147483648);
CREATE TABLE saas_subscriptions (
 company_id UUID PRIMARY KEY REFERENCES companies(id), plan VARCHAR(12) NOT NULL REFERENCES saas_plans(code),
 state VARCHAR(12) NOT NULL CHECK(state IN ('TRIAL','ACTIVE','LEGACY','SIMULATED')),
 trial_ends_at TIMESTAMP WITH TIME ZONE NOT NULL, paid_until TIMESTAMP WITH TIME ZONE,
 cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE, pending_request_id UUID,
 revision BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
INSERT INTO saas_subscriptions(company_id,plan,state,trial_ends_at,created_at,updated_at)
 SELECT id,'BUSINESS','LEGACY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM companies;
CREATE TABLE saas_plan_requests (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id), plan VARCHAR(12) NOT NULL REFERENCES saas_plans(code),
 status VARCHAR(12) NOT NULL CHECK(status IN ('PENDING','APPROVED','CANCELLED')),
 reference VARCHAR(120), granted_days INTEGER, active_until TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX ix_saas_requests_company ON saas_plan_requests(company_id,created_at,id);
