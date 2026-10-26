CREATE TABLE reconciliation_batches (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    company_id UUID NOT NULL REFERENCES companies(id),
    account_id UUID NOT NULL REFERENCES financial_accounts(id),
    entry_id UUID NOT NULL REFERENCES financial_entries(id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL,
    reason VARCHAR(255) NOT NULL
);

CREATE TABLE statement_lines_v21 (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    account_id UUID NOT NULL REFERENCES financial_accounts(id),
    source_id UUID NOT NULL,
    fit_id VARCHAR(255) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    posted_date DATE NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount <> 0),
    status VARCHAR(8) NOT NULL CHECK (status IN ('OPEN','LINKED','IGNORED')),
    entry_id UUID REFERENCES financial_entries(id),
    reason VARCHAR(255),
    reconciliation_batch_id UUID REFERENCES reconciliation_batches(id),
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(source_id,fit_id),
    FOREIGN KEY(source_id,company_id,account_id) REFERENCES statement_sources(id,company_id,account_id),
    CHECK((status='LINKED' AND entry_id IS NOT NULL) OR (status<>'LINKED' AND entry_id IS NULL))
);

INSERT INTO statement_lines_v21 (
    id,company_id,account_id,source_id,fit_id,fingerprint,description,posted_date,amount,
    status,entry_id,reason,revision,created_at,updated_at
)
SELECT id,company_id,account_id,source_id,fit_id,fingerprint,description,posted_date,amount,
       status,entry_id,reason,revision,created_at,updated_at
FROM statement_lines;
DROP TABLE statement_lines;
ALTER TABLE statement_lines_v21 RENAME TO statement_lines;
ALTER TABLE entry_payments ADD COLUMN reconciliation_batch_id UUID REFERENCES reconciliation_batches(id);

CREATE INDEX ix_reconciliation_batches_company_date
    ON reconciliation_batches(company_id, account_id, payment_date DESC);
CREATE INDEX ix_statement_lines_batch ON statement_lines(reconciliation_batch_id);
CREATE INDEX ix_entry_payments_reconciliation_batch ON entry_payments(reconciliation_batch_id);
CREATE INDEX ix_statement_lines_review ON statement_lines(company_id,account_id,status,posted_date,id);
