ALTER TABLE financial_entries ADD CONSTRAINT uq_entry_company UNIQUE(id,company_id);
CREATE TABLE entry_attachments (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    entry_id UUID NOT NULL,
    filename VARCHAR(120) NOT NULL,
    content_type VARCHAR(40) NOT NULL CHECK(content_type IN ('application/pdf','image/png','image/jpeg')),
    byte_count BIGINT NOT NULL CHECK(byte_count BETWEEN 1 AND 5242880),
    uploaded_by UUID NOT NULL REFERENCES app_users(id),
    uploaded_by_name VARCHAR(120) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attachment_entry_company FOREIGN KEY(entry_id,company_id) REFERENCES financial_entries(id,company_id)
);
CREATE INDEX ix_attachments_entry ON entry_attachments(company_id,entry_id,created_at);
