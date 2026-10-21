CREATE TABLE saas_invoices (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id),
 request_id UUID NOT NULL UNIQUE REFERENCES saas_plan_requests(id),
 plan VARCHAR(12) NOT NULL REFERENCES saas_plans(code), environment VARCHAR(12) NOT NULL,
 merchant VARCHAR(100) NOT NULL, amount NUMERIC(12,2) NOT NULL CHECK(amount>=5),
 due_date DATE NOT NULL, period_days INTEGER NOT NULL DEFAULT 30 CHECK(period_days=30), method VARCHAR(12) NOT NULL CHECK(method IN ('PIX','BOLETO')),
 status VARCHAR(12) NOT NULL CHECK(status IN ('ISSUING','PENDING','CONFIRMED','OVERDUE','PAID','TEST_PAID','CANCELLING','CANCELLED','REVIEW')),
 provider_id VARCHAR(100) UNIQUE, customer_id VARCHAR(100), invoice_url VARCHAR(2048),
 message VARCHAR(255), active_until TIMESTAMP WITH TIME ZONE, released BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX ix_saas_invoices_company ON saas_invoices(company_id,created_at,id);
