CREATE TABLE billing_connections (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id),
 environment VARCHAR(10) NOT NULL CHECK(environment IN ('SANDBOX','PRODUCTION')),
 active BOOLEAN NOT NULL, api_key VARCHAR(4096) NOT NULL, webhook_token VARCHAR(4096) NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, UNIQUE(company_id,environment)
);
CREATE TABLE billing_charges (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id),
 entry_id UUID NOT NULL UNIQUE REFERENCES financial_entries(id),
 connection_id UUID NOT NULL REFERENCES billing_connections(id),
 method VARCHAR(6) NOT NULL CHECK(method IN ('PIX','BOLETO')),
 status VARCHAR(24) NOT NULL, provider_id VARCHAR(100), customer_id VARCHAR(100),
 description VARCHAR(255) NOT NULL, amount NUMERIC(14,2) NOT NULL, due_date DATE NOT NULL,
 provider_status VARCHAR(80), invoice_url VARCHAR(2048), bank_slip_url VARCHAR(2048),
 net_amount NUMERIC(14,2), settled_date DATE, fee_entry_id UUID UNIQUE REFERENCES financial_entries(id),
 message VARCHAR(255), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 UNIQUE(connection_id,provider_id)
);
CREATE INDEX ix_billing_charges_company ON billing_charges(company_id,created_at,id);
