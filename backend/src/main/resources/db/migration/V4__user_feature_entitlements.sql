-- ============================================================
-- V4__user_feature_entitlements.sql — per-user feature toggles
-- ============================================================

CREATE TABLE user_feature_entitlements (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feature    VARCHAR(30) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_user_feature_entitlements_feature
        CHECK (feature IN ('TRANSACTIONS', 'CATEGORIES', 'BUDGETS', 'REPORTS', 'AI_ASSISTANT')),
    CONSTRAINT uq_user_feature_entitlements_user_feature UNIQUE (user_id, feature)
);

CREATE INDEX idx_user_feature_entitlements_user_id ON user_feature_entitlements (user_id);
