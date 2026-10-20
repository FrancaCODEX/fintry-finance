CREATE TABLE products (
 id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES companies(id),
 sku VARCHAR(40) NOT NULL,name VARCHAR(120) NOT NULL,unit VARCHAR(8) NOT NULL CHECK(unit IN ('UN','KG','L','M','CX')),
 price NUMERIC(14,2) NOT NULL CHECK(price>=0),cost NUMERIC(14,2) NOT NULL CHECK(cost>=0),
 quantity NUMERIC(12,3) NOT NULL CHECK(quantity>=0),minimum NUMERIC(12,3) NOT NULL CHECK(minimum>=0),active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,UNIQUE(company_id,sku),UNIQUE(id,company_id)
);
CREATE INDEX ix_products_company_name ON products(company_id,name);
CREATE TABLE sales (
 id UUID PRIMARY KEY,company_id UUID NOT NULL REFERENCES companies(id),number BIGINT NOT NULL,
 request_id UUID NOT NULL,fingerprint VARCHAR(64) NOT NULL,status VARCHAR(12) NOT NULL CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED')),
 contact_id UUID REFERENCES contacts(id),contact_name VARCHAR(120),notes VARCHAR(500),total NUMERIC(14,2) NOT NULL CHECK(total>0),
 entry_id UUID UNIQUE REFERENCES financial_entries(id),confirmed_at TIMESTAMP WITH TIME ZONE,cancelled_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,UNIQUE(company_id,number),UNIQUE(company_id,request_id),UNIQUE(id,company_id)
);
CREATE INDEX ix_sales_company_number ON sales(company_id,number);
CREATE TABLE sale_items (
 id UUID PRIMARY KEY,sale_id UUID NOT NULL REFERENCES sales(id),product_id UUID NOT NULL REFERENCES products(id),
 sku VARCHAR(40) NOT NULL,product_name VARCHAR(120) NOT NULL,unit VARCHAR(8) NOT NULL,
 quantity NUMERIC(12,3) NOT NULL CHECK(quantity>0),unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price>=0),total NUMERIC(14,2) NOT NULL CHECK(total>=0),
 created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,UNIQUE(sale_id,product_id)
);
CREATE TABLE stock_movements (
 id UUID PRIMARY KEY,company_id UUID NOT NULL REFERENCES companies(id),product_id UUID NOT NULL,sale_id UUID,
 request_id UUID,fingerprint VARCHAR(64),kind VARCHAR(8) NOT NULL CHECK(kind IN ('INITIAL','IN','OUT','SALE','RETURN')),
 product_name VARCHAR(120) NOT NULL,sku VARCHAR(40) NOT NULL,unit VARCHAR(8) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,delta NUMERIC(12,3) NOT NULL CHECK(delta<>0),balance NUMERIC(12,3) NOT NULL CHECK(balance>=0),reason VARCHAR(255) NOT NULL,actor_name VARCHAR(120) NOT NULL,
 created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,UNIQUE(company_id,request_id),
 FOREIGN KEY(product_id,company_id) REFERENCES products(id,company_id),FOREIGN KEY(sale_id,company_id) REFERENCES sales(id,company_id)
);
CREATE INDEX ix_stock_company_date ON stock_movements(company_id,occurred_at);
ALTER TABLE financial_entries ADD COLUMN sale_id UUID REFERENCES sales(id);
CREATE UNIQUE INDEX ux_entry_sale ON financial_entries(sale_id);
