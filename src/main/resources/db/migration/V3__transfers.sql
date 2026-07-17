CREATE TABLE transfers (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key TEXT        NOT NULL,
    customer_id     BIGINT      NOT NULL REFERENCES customers (id),
    from_account_id BIGINT      NOT NULL REFERENCES accounts (id),
    to_account_id   BIGINT      NOT NULL REFERENCES accounts (id),
    amount_cents    BIGINT      NOT NULL CHECK (amount_cents > 0),
    status          TEXT        NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    -- Replay of the same client key must return the original transfer, never create a second one.
    CONSTRAINT uq_transfers_customer_key UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ck_transfers_distinct_accounts CHECK (from_account_id <> to_account_id)
);

ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_entries_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id);
