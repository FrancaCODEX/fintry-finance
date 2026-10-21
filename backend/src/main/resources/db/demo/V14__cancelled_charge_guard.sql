ALTER TABLE billing_charges ADD COLUMN released BOOLEAN NOT NULL DEFAULT FALSE;
-- Preserve cancellation history when a provider-side restoration is received later.
UPDATE billing_charges SET released=TRUE WHERE status='CANCELLED';
