CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    account_type    VARCHAR(32)  NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_code UNIQUE (code),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE journal_entries (
    id                UUID PRIMARY KEY,
    idempotency_key   VARCHAR(128) NOT NULL,
    description       VARCHAR(512) NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    voided_at         TIMESTAMPTZ,
    voids_entry_id    UUID REFERENCES journal_entries (id),
    CONSTRAINT uq_journal_entries_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_journal_entries_status CHECK (status IN ('POSTED', 'VOIDED'))
);

CREATE TABLE ledger_lines (
    id                UUID PRIMARY KEY,
    journal_entry_id  UUID         NOT NULL REFERENCES journal_entries (id),
    account_id        UUID         NOT NULL REFERENCES accounts (id),
    direction         VARCHAR(8)   NOT NULL,
    amount_cents      BIGINT       NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ledger_lines_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_lines_amount_positive CHECK (amount_cents > 0),
    CONSTRAINT ck_ledger_lines_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_ledger_lines_account_id ON ledger_lines (account_id);
CREATE INDEX idx_ledger_lines_journal_entry_id ON ledger_lines (journal_entry_id);
CREATE INDEX idx_journal_entries_created_at ON journal_entries (created_at DESC);
CREATE INDEX idx_accounts_status ON accounts (status);

-- Request fingerprint for idempotency conflict detection (same key, different body).
CREATE TABLE idempotency_records (
    idempotency_key   VARCHAR(128) PRIMARY KEY,
    request_hash      VARCHAR(64)  NOT NULL,
    journal_entry_id  UUID         NOT NULL REFERENCES journal_entries (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
