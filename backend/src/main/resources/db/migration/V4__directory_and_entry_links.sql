CREATE TABLE contacts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('CUSTOMER','SUPPLIER','BOTH')),
    document VARCHAR(14), email VARCHAR(255), phone VARCHAR(24), notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    UNIQUE(company_id,document), UNIQUE(id,company_id)
);
CREATE INDEX ix_contacts_company_name ON contacts(company_id,name);
CREATE TABLE financial_categories (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(80) NOT NULL, name_key VARCHAR(80) NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('INCOME','EXPENSE','BOTH')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    UNIQUE(company_id,name_key), UNIQUE(id,company_id)
);
ALTER TABLE financial_entries ADD COLUMN category_id UUID;
ALTER TABLE financial_entries ADD COLUMN contact_id UUID;
ALTER TABLE financial_entries ADD COLUMN contact_name VARCHAR(120);
ALTER TABLE financial_entries ADD CONSTRAINT fk_entries_category FOREIGN KEY(category_id,company_id) REFERENCES financial_categories(id,company_id);
ALTER TABLE financial_entries ADD CONSTRAINT fk_entries_contact FOREIGN KEY(contact_id,company_id) REFERENCES contacts(id,company_id);
CREATE INDEX ix_entries_category ON financial_entries(company_id,category_id);
CREATE INDEX ix_entries_contact ON financial_entries(company_id,contact_id);
