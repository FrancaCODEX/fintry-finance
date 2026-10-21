ALTER TABLE products ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'PRODUCT' CHECK (kind IN ('PRODUCT','SERVICE'));
ALTER TABLE products ADD CONSTRAINT ck_service_no_stock CHECK (kind='PRODUCT' OR (quantity=0 AND minimum=0 AND unit='UN'));
ALTER TABLE sale_items ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'PRODUCT' CHECK (kind IN ('PRODUCT','SERVICE'));
CREATE INDEX ix_products_company_kind ON products(company_id,kind,active,name);
CREATE TABLE proposals (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id), number BIGINT NOT NULL,
 request_id UUID NOT NULL, fingerprint VARCHAR(64) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 title VARCHAR(120) NOT NULL, status VARCHAR(12) NOT NULL CHECK(status IN ('DRAFT','OPEN','APPROVED','REJECTED','CANCELLED')),
 contact_id UUID REFERENCES contacts(id), contact_name VARCHAR(120), notes VARCHAR(500), valid_until DATE NOT NULL,
 total NUMERIC(14,2) NOT NULL CHECK(total>0), sale_id UUID UNIQUE,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 UNIQUE(company_id,number), UNIQUE(company_id,request_id),
 FOREIGN KEY(sale_id,company_id) REFERENCES sales(id,company_id)
);
CREATE INDEX ix_proposals_company_status ON proposals(company_id,status,number);
CREATE TABLE proposal_items (
 id UUID PRIMARY KEY, proposal_id UUID NOT NULL REFERENCES proposals(id), product_id UUID NOT NULL REFERENCES products(id),
 kind VARCHAR(8) NOT NULL CHECK(kind IN ('PRODUCT','SERVICE')), sku VARCHAR(40) NOT NULL, product_name VARCHAR(120) NOT NULL, unit VARCHAR(8) NOT NULL,
 quantity NUMERIC(12,3) NOT NULL CHECK(quantity>0), unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price>=0), total NUMERIC(14,2) NOT NULL CHECK(total>=0),
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, UNIQUE(proposal_id,product_id)
);
