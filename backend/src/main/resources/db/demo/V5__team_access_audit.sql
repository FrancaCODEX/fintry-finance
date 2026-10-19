ALTER TABLE app_users ADD COLUMN role VARCHAR(12) NOT NULL DEFAULT 'ADMIN' CHECK(role IN ('ADMIN','FINANCE','VIEWER'));
ALTER TABLE app_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ADD COLUMN invited BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX ix_users_company_role ON app_users(company_id,role,active);
CREATE TABLE access_tokens (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE, kind VARCHAR(8) NOT NULL CHECK(kind IN ('RESET','INVITE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL, used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_access_tokens_user ON access_tokens(user_id,kind,used_at);
CREATE TABLE audit_events (
    id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id), actor_id UUID REFERENCES app_users(id),
    actor_name VARCHAR(120) NOT NULL, action VARCHAR(32) NOT NULL, resource VARCHAR(24) NOT NULL, resource_id UUID,
    label VARCHAR(255) NOT NULL, changes_json TEXT NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_audit_company_date ON audit_events(company_id,occurred_at);
