CREATE TABLE reconciliation_rules (
 id UUID PRIMARY KEY, company_id UUID NOT NULL, account_id UUID NOT NULL,
 name VARCHAR(120) NOT NULL, author VARCHAR(120) NOT NULL, bank_text VARCHAR(80) NOT NULL, entry_text VARCHAR(80) NOT NULL,
 direction VARCHAR(10) NOT NULL, window_days INTEGER NOT NULL, active BOOLEAN NOT NULL,
 creation_key UUID NOT NULL, creation_hash VARCHAR(64) NOT NULL, revision BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_reconciliation_rule_key UNIQUE(company_id,creation_key)
);
CREATE INDEX idx_reconciliation_rules_scope ON reconciliation_rules(company_id,account_id,active);

ALTER TABLE statement_lines ADD COLUMN decision_evidence TEXT;
