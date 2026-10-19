CREATE TABLE financial_series (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    request_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('INSTALLMENT','RECURRING')),
    type VARCHAR(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    occurrences INTEGER NOT NULL CHECK (occurrences BETWEEN 2 AND 120),
    total_amount DECIMAL(18,2) NOT NULL CHECK (total_amount > 0),
    first_due_date DATE NOT NULL, last_due_date DATE NOT NULL,
    stopped BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    UNIQUE(company_id,request_id), UNIQUE(id,company_id)
);
ALTER TABLE financial_entries ADD COLUMN series_id UUID;
ALTER TABLE financial_entries ADD COLUMN sequence_number INTEGER;
ALTER TABLE financial_entries ADD COLUMN series_count INTEGER;
ALTER TABLE financial_entries ADD COLUMN series_kind VARCHAR(12);
ALTER TABLE financial_entries ADD CONSTRAINT fk_entries_series FOREIGN KEY(series_id,company_id) REFERENCES financial_series(id,company_id);
ALTER TABLE financial_entries ADD CONSTRAINT uq_entries_series_sequence UNIQUE(series_id,sequence_number);
ALTER TABLE financial_entries ADD CONSTRAINT ck_entries_series_fields CHECK (
    (series_id IS NULL AND sequence_number IS NULL AND series_count IS NULL AND series_kind IS NULL)
    OR (series_id IS NOT NULL AND sequence_number IS NOT NULL AND series_count IS NOT NULL AND series_kind IS NOT NULL
        AND sequence_number BETWEEN 1 AND series_count AND series_count BETWEEN 2 AND 120 AND series_kind IN ('INSTALLMENT','RECURRING'))
);
CREATE INDEX ix_series_company ON financial_series(company_id,created_at);
