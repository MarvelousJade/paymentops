CREATE TABLE accounts (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT      NOT NULL REFERENCES customers (id),
    type          TEXT        NOT NULL CHECK (type IN ('CHEQUING', 'SAVINGS')),
    status        TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'FROZEN', 'CLOSED')),
    -- Materialized convenience; the ledger is the source of truth. Reconciliation catches drift.
    balance_cents BIGINT      NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_customer ON accounts (customer_id);

-- Append-only. No UPDATE/DELETE ever; corrections are new entries.
CREATE TABLE ledger_entries (
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT      NOT NULL REFERENCES accounts (id),
    direction    TEXT        NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_cents BIGINT      NOT NULL CHECK (amount_cents > 0),
    transfer_id  BIGINT, -- FK added in V3 once transfers exist; NULL for deposits
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account_created ON ledger_entries (account_id, created_at);

CREATE TABLE account_status_changes (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES accounts (id),
    old_status TEXT        NOT NULL,
    new_status TEXT        NOT NULL,
    changed_by BIGINT      NOT NULL REFERENCES customers (id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
