ALTER TABLE entry_payments ADD COLUMN request_id UUID;
CREATE UNIQUE INDEX uq_entry_payment_request ON entry_payments(company_id, request_id);
