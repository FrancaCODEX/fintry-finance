CREATE INDEX ix_entries_company_status_due ON financial_entries(company_id,status,due_date,id);
