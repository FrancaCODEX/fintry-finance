ALTER TABLE financial_entries DROP CONSTRAINT IF EXISTS financial_entries_status_check;
ALTER TABLE financial_entries
    ADD CONSTRAINT financial_entries_status_check
    CHECK (status IN ('PENDING','PARTIAL','PAID','CANCELLED'));
