CREATE TABLE companies (
                           id UUID PRIMARY KEY,

                           legal_name VARCHAR(255) NOT NULL,

                           trade_name VARCHAR(255),

                           document VARCHAR(20) NOT NULL UNIQUE,

                           email VARCHAR(255),

                           phone VARCHAR(20),

                           created_at TIMESTAMP NOT NULL,

                           updated_at TIMESTAMP NOT NULL
);
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE TABLE financial_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(100) NOT NULL,
    initial_balance DECIMAL(14,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(company_id, name),
    UNIQUE(id, company_id)
);
CREATE TABLE financial_entries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    account_id UUID NOT NULL,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(80) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    amount DECIMAL(14,2) NOT NULL CHECK (amount > 0),
    due_date DATE NOT NULL,
    payment_date DATE,
    status VARCHAR(10) NOT NULL CONSTRAINT financial_entries_status_check CHECK (status IN ('PENDING','PAID','CANCELLED')),
    transfer_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (account_id, company_id) REFERENCES financial_accounts(id, company_id),
    CHECK (status <> 'PAID' OR payment_date IS NOT NULL)
);
CREATE INDEX ix_entries_company_due ON financial_entries(company_id, due_date);
CREATE INDEX ix_entries_account ON financial_entries(account_id, company_id, status);
