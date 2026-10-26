ALTER TABLE financial_entries ADD COLUMN paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
CREATE TABLE entry_payments (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    company_id UUID NOT NULL REFERENCES companies(id),
    entry_id UUID NOT NULL REFERENCES financial_entries(id),
    account_id UUID NOT NULL REFERENCES financial_accounts(id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL
);

INSERT INTO entry_payments(id,created_at,updated_at,company_id,entry_id,account_id,amount,payment_date)
SELECT id,created_at,updated_at,company_id,id,account_id,amount,COALESCE(payment_date,due_date)
FROM financial_entries
WHERE status='PAID';

UPDATE financial_entries SET paid_amount=amount WHERE status='PAID';

CREATE INDEX ix_entry_payments_company_date ON entry_payments(company_id,payment_date);
CREATE INDEX ix_entry_payments_entry ON entry_payments(entry_id,payment_date);
