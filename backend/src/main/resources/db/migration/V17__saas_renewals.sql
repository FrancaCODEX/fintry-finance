ALTER TABLE saas_invoices ADD COLUMN renewal_period_end TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX ux_saas_invoice_renewal_cycle ON saas_invoices(company_id,renewal_period_end);
CREATE TABLE saas_renewals (
 company_id UUID PRIMARY KEY REFERENCES companies(id), enabled BOOLEAN NOT NULL,
 plan VARCHAR(12) NOT NULL REFERENCES saas_plans(code), amount NUMERIC(12,2) NOT NULL CHECK(amount>=5),
 method VARCHAR(12) NOT NULL CHECK(method IN ('PIX','BOLETO')), environment VARCHAR(12) NOT NULL,
 merchant VARCHAR(100) NOT NULL, customer_id VARCHAR(100) NOT NULL,
 source_invoice_id UUID NOT NULL REFERENCES saas_invoices(id), accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
 accepted_by UUID NOT NULL REFERENCES app_users(id), next_check_at TIMESTAMP WITH TIME ZONE NOT NULL,
 last_period_end TIMESTAMP WITH TIME ZONE, last_invoice_id UUID REFERENCES saas_invoices(id), revision BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_saas_renewals_due ON saas_renewals(enabled,next_check_at,company_id);
