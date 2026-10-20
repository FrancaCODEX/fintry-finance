CREATE TABLE category_budgets (
 id UUID PRIMARY KEY,
 company_id UUID NOT NULL REFERENCES companies(id),
 category_id UUID NOT NULL REFERENCES financial_categories(id),
 period_start DATE NOT NULL,
 limit_amount NUMERIC(14,2) NOT NULL CHECK (limit_amount > 0),
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_budget_company_category_month UNIQUE(company_id,category_id,period_start),
 CONSTRAINT ck_budget_month CHECK (EXTRACT(DAY FROM period_start)=1)
);
CREATE INDEX ix_budget_company_month ON category_budgets(company_id,period_start);
CREATE TABLE sample_runs (
 company_id UUID PRIMARY KEY REFERENCES companies(id),
 completed_at TIMESTAMP NOT NULL,
 pack_version INTEGER NOT NULL
);
