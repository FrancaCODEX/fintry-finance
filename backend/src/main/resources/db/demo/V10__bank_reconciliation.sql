CREATE TABLE statement_sources (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id), account_id UUID NOT NULL REFERENCES financial_accounts(id),
 source_hash VARCHAR(64) NOT NULL, bank VARCHAR(40) NOT NULL, account_hint VARCHAR(8) NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 UNIQUE(company_id,source_hash), UNIQUE(id,company_id,account_id)
);
CREATE TABLE statement_lines (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id), account_id UUID NOT NULL REFERENCES financial_accounts(id),
 source_id UUID NOT NULL, fit_id VARCHAR(255) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
 description VARCHAR(255) NOT NULL, posted_date DATE NOT NULL, amount NUMERIC(14,2) NOT NULL CHECK(amount<>0),
 status VARCHAR(8) NOT NULL CHECK(status IN ('OPEN','LINKED','IGNORED')), entry_id UUID UNIQUE REFERENCES financial_entries(id),
 reason VARCHAR(255), revision BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 UNIQUE(source_id,fit_id), FOREIGN KEY(source_id,company_id,account_id) REFERENCES statement_sources(id,company_id,account_id),
 CHECK((status='LINKED' AND entry_id IS NOT NULL) OR (status<>'LINKED' AND entry_id IS NULL))
);
CREATE INDEX ix_statement_lines_review ON statement_lines(company_id,account_id,status,posted_date,id);
CREATE INDEX ix_entries_reconciliation ON financial_entries(company_id,account_id,type,amount,status);
