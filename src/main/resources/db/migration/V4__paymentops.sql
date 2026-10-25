CREATE TABLE payments (
    id                      UUID PRIMARY KEY,
    merchant_id             BIGINT      NOT NULL REFERENCES customers (id),
    idempotency_key         TEXT        NOT NULL,
    source_account_id       BIGINT      NOT NULL REFERENCES accounts (id),
    destination_account_id  BIGINT      NOT NULL REFERENCES accounts (id),
    amount_cents            BIGINT      NOT NULL CHECK (amount_cents > 0),
    refunded_amount_cents   BIGINT      NOT NULL DEFAULT 0 CHECK (refunded_amount_cents >= 0),
    currency                VARCHAR(3)  NOT NULL DEFAULT 'CAD',
    payment_token           TEXT        NOT NULL,
    status                  TEXT        NOT NULL CHECK (status IN (
        'REQUESTED', 'AUTHORIZED', 'REVIEW_REQUIRED', 'REJECTED',
        'CAPTURE_PENDING', 'CAPTURED', 'SETTLED', 'CANCELLED',
        'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED')),
    correlation_id          UUID        NOT NULL,
    version                 BIGINT      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_merchant_key UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT ck_payments_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT ck_payments_refund_total CHECK (refunded_amount_cents <= amount_cents),
    CONSTRAINT ck_payments_synthetic_token CHECK (payment_token LIKE 'tok_%')
);

CREATE INDEX idx_payments_correlation ON payments (correlation_id);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);
CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at);

CREATE TABLE risk_decisions (
    id              UUID PRIMARY KEY,
    payment_id      UUID        NOT NULL REFERENCES payments (id),
    source_event_id UUID        NOT NULL,
    outcome         TEXT        NOT NULL CHECK (outcome IN ('APPROVE', 'REVIEW', 'REJECT')),
    rule_code       TEXT        NOT NULL,
    explanation     TEXT        NOT NULL,
    decided_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_risk_payment UNIQUE (payment_id),
    CONSTRAINT uq_risk_event UNIQUE (source_event_id)
);

-- Immutable business timeline. Corrections are appended; existing rows are never changed.
CREATE TABLE payment_events (
    id              UUID PRIMARY KEY,
    payment_id      UUID        NOT NULL REFERENCES payments (id),
    event_id        UUID        NOT NULL,
    event_type      TEXT        NOT NULL,
    from_status     TEXT,
    to_status       TEXT,
    actor           TEXT        NOT NULL,
    details         TEXT        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_timeline_event UNIQUE (event_id)
);

CREATE INDEX idx_payment_events_payment_time ON payment_events (payment_id, occurred_at);

CREATE TABLE outbox_events (
    event_id        UUID PRIMARY KEY,
    event_type      TEXT        NOT NULL,
    aggregate_id    TEXT        NOT NULL,
    correlation_id  UUID        NOT NULL,
    causation_id    UUID,
    schema_version  INTEGER     NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    producer        TEXT        NOT NULL,
    payload         TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD_LETTER')),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    last_error      TEXT
);

CREATE INDEX idx_outbox_pending ON outbox_events (status, next_attempt_at, occurred_at);

CREATE TABLE processed_events (
    consumer_name TEXT        NOT NULL,
    event_id      UUID        NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE payment_refunds (
    id              UUID PRIMARY KEY,
    payment_id      UUID        NOT NULL REFERENCES payments (id),
    idempotency_key TEXT        NOT NULL,
    amount_cents    BIGINT      NOT NULL CHECK (amount_cents > 0),
    status          TEXT        NOT NULL CHECK (status IN ('REQUESTED', 'COMPLETED', 'REJECTED')),
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT uq_refund_payment_key UNIQUE (payment_id, idempotency_key)
);

CREATE TABLE payment_ledger_postings (
    id               UUID PRIMARY KEY,
    payment_id       UUID        NOT NULL REFERENCES payments (id),
    operation_key    TEXT        NOT NULL,
    source_event_id  UUID        NOT NULL,
    debit_entry_id   BIGINT      NOT NULL REFERENCES ledger_entries (id),
    credit_entry_id  BIGINT      NOT NULL REFERENCES ledger_entries (id),
    amount_cents     BIGINT      NOT NULL CHECK (amount_cents > 0),
    posted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_posting_operation UNIQUE (payment_id, operation_key),
    CONSTRAINT uq_payment_posting_event UNIQUE (source_event_id),
    CONSTRAINT ck_payment_posting_entries CHECK (debit_entry_id <> credit_entry_id)
);

ALTER TABLE ledger_entries ADD COLUMN payment_id UUID REFERENCES payments (id);
ALTER TABLE ledger_entries ADD COLUMN source_event_id UUID;
CREATE UNIQUE INDEX uq_ledger_payment_event_side
    ON ledger_entries (payment_id, source_event_id, account_id, direction)
    WHERE payment_id IS NOT NULL;

CREATE TABLE settlement_records (
    id                  UUID PRIMARY KEY,
    payment_id          UUID REFERENCES payments (id),
    external_reference  TEXT        NOT NULL,
    amount_cents        BIGINT      NOT NULL CHECK (amount_cents > 0),
    currency            VARCHAR(3)  NOT NULL,
    status              TEXT        NOT NULL CHECK (status IN ('SETTLED', 'REJECTED', 'DELAYED')),
    source_event_id     UUID,
    settled_at          TIMESTAMPTZ,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    raw_record          TEXT        NOT NULL
);

CREATE INDEX idx_settlement_payment ON settlement_records (payment_id, received_at);
CREATE INDEX idx_settlement_external_reference ON settlement_records (external_reference);

CREATE TABLE reconciliation_exceptions (
    id                  UUID PRIMARY KEY,
    payment_id          UUID REFERENCES payments (id),
    classification      TEXT        NOT NULL CHECK (classification IN (
        'MISSING_SETTLEMENT', 'EXTERNAL_ONLY', 'AMOUNT_MISMATCH', 'DUPLICATE_SETTLEMENT', 'LATE_SETTLEMENT')),
    status              TEXT        NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    expected_amount     BIGINT,
    actual_amount       BIGINT,
    evidence            TEXT        NOT NULL,
    first_detected_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_detected_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT uq_reconciliation_payment_class UNIQUE (payment_id, classification)
);

CREATE INDEX idx_reconciliation_status ON reconciliation_exceptions (status, first_detected_at);

CREATE TABLE reconciliation_actions (
    id              BIGSERIAL PRIMARY KEY,
    exception_id    UUID        NOT NULL REFERENCES reconciliation_exceptions (id),
    action          TEXT        NOT NULL CHECK (action IN ('DETECTED', 'ACKNOWLEDGED', 'RESOLVED', 'REOPENED')),
    actor           TEXT        NOT NULL,
    notes           TEXT        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY,
    payment_id      UUID        NOT NULL REFERENCES payments (id),
    source_event_id UUID        NOT NULL,
    endpoint        TEXT        NOT NULL,
    payload         TEXT        NOT NULL,
    signature       TEXT        NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'RETRYING', 'DEAD_LETTER')),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_webhook_event UNIQUE (source_event_id)
);

CREATE INDEX idx_webhook_due ON webhook_deliveries (status, next_attempt_at);

CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID        NOT NULL UNIQUE,
    actor           TEXT        NOT NULL,
    action          TEXT        NOT NULL,
    aggregate_type  TEXT        NOT NULL,
    aggregate_id    TEXT        NOT NULL,
    correlation_id  UUID        NOT NULL,
    details         TEXT        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_aggregate ON audit_events (aggregate_type, aggregate_id, occurred_at);

-- Database-level immutability for evidence and financial history.
CREATE OR REPLACE FUNCTION reject_immutable_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_events_immutable
    BEFORE UPDATE OR DELETE ON payment_events
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER audit_events_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER reconciliation_actions_immutable
    BEFORE UPDATE OR DELETE ON reconciliation_actions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
