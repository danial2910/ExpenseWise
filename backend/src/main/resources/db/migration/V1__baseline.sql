-- ============================================================
-- V1__baseline.sql — ExpenseWise foundation schema
-- ============================================================

-- ---------- users ----------
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- ---------- categories ----------
CREATE TABLE categories (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id   BIGINT REFERENCES users(id) ON DELETE CASCADE,
    name      VARCHAR(100) NOT NULL,
    type      VARCHAR(10)  NOT NULL,
    icon      VARCHAR(50),
    is_system BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_categories_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT uq_categories_user_name_type UNIQUE (user_id, name, type)
);

-- Partial unique index: NULL is distinct in Postgres uniqueness, so a plain
-- UNIQUE(user_id, name, type) would allow duplicate system categories.
CREATE UNIQUE INDEX uq_categories_system_name_type
    ON categories (name, type)
    WHERE user_id IS NULL;

-- ---------- recurring_rules ----------
CREATE TABLE recurring_rules (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id    BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    type           VARCHAR(10) NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    description    VARCHAR(255),
    frequency      VARCHAR(10) NOT NULL,
    start_date     DATE NOT NULL,
    end_date       DATE,
    next_due_date  DATE NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_recurring_rules_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_recurring_rules_frequency CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    CONSTRAINT chk_recurring_rules_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_recurring_rules_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

-- ---------- transactions ----------
CREATE TABLE transactions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id       BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    recurring_rule_id BIGINT REFERENCES recurring_rules(id) ON DELETE SET NULL,
    type              VARCHAR(10) NOT NULL,
    amount            DECIMAL(12,2) NOT NULL,
    transaction_date  DATE NOT NULL,
    description       VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

-- ---------- receipts (1:1 with transactions) ----------
CREATE TABLE receipts (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    storage_path   VARCHAR(500) NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    mime_type      VARCHAR(100) NOT NULL,
    size_bytes     BIGINT NOT NULL,
    uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_receipts_transaction_id UNIQUE (transaction_id),
    CONSTRAINT chk_receipts_size_positive CHECK (size_bytes > 0)
);

-- ---------- budgets ----------
CREATE TABLE budgets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    amount       DECIMAL(12,2) NOT NULL,
    period_month DATE NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_budgets_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_budgets_period_month_first_of_month
        CHECK (period_month = date_trunc('month', period_month)::date)
);

CREATE UNIQUE INDEX uq_budgets_user_category_month
    ON budgets (user_id, category_id, period_month)
    WHERE category_id IS NOT NULL;

CREATE UNIQUE INDEX uq_budgets_user_month_overall
    ON budgets (user_id, period_month)
    WHERE category_id IS NULL;

-- ---------- password_reset_tokens ----------
CREATE TABLE password_reset_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash)
);

-- ---------- notifications ----------
CREATE TABLE notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- activity_logs ----------
CREATE TABLE activity_logs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   BIGINT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- ai_conversations ----------
CREATE TABLE ai_conversations (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- ai_messages ----------
CREATE TABLE ai_messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_messages_role CHECK (role IN ('user', 'assistant'))
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date DESC);
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category_id);
CREATE INDEX idx_transactions_user_type ON transactions (user_id, type);
CREATE INDEX idx_budgets_user_month ON budgets (user_id, period_month);
CREATE INDEX idx_recurring_rules_next_due ON recurring_rules (next_due_date) WHERE is_active;
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);
CREATE INDEX idx_activity_logs_user_created ON activity_logs (user_id, created_at DESC);

-- ============================================================
-- Seed data: system categories (user_id NULL, is_system TRUE)
-- ============================================================
INSERT INTO categories (user_id, name, type, icon, is_system) VALUES
    (NULL, 'Food',          'EXPENSE', 'utensils',     TRUE),
    (NULL, 'Transport',     'EXPENSE', 'car',          TRUE),
    (NULL, 'Bills',         'EXPENSE', 'receipt',      TRUE),
    (NULL, 'Entertainment', 'EXPENSE', 'film',         TRUE),
    (NULL, 'Shopping',      'EXPENSE', 'shopping-bag', TRUE),
    (NULL, 'Health',        'EXPENSE', 'heart',        TRUE),
    (NULL, 'Education',     'EXPENSE', 'book',         TRUE),
    (NULL, 'Other',         'EXPENSE', 'ellipsis',     TRUE),
    (NULL, 'Salary',        'INCOME',  'briefcase',    TRUE),
    (NULL, 'Freelance',     'INCOME',  'laptop',       TRUE),
    (NULL, 'Investment',    'INCOME',  'trending-up',  TRUE),
    (NULL, 'Allowance',     'INCOME',  'gift',         TRUE),
    (NULL, 'Other',         'INCOME',  'ellipsis',     TRUE);
