-- ============================================================
-- V2__refresh_tokens.sql — refresh token rotation storage
-- ============================================================

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- token_hash already has an index via the UNIQUE constraint above.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
