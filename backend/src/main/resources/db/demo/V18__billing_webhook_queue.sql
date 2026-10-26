CREATE TABLE billing_webhook_events (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    connection_id UUID NOT NULL REFERENCES billing_connections(id),
    provider_event_id VARCHAR(100) NOT NULL,
    provider_payment_id VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    last_error VARCHAR(160),
    CONSTRAINT uq_billing_webhook_event UNIQUE(connection_id, provider_event_id),
    CONSTRAINT ck_billing_webhook_event_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_billing_webhook_event_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_billing_webhook_event_queue
    ON billing_webhook_events(status, next_attempt_at, created_at);
