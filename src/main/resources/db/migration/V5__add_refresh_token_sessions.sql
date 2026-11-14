CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_version INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX ix_auth_refresh_tokens_session_id ON auth_refresh_tokens (session_id);
CREATE INDEX ix_auth_refresh_tokens_user_id ON auth_refresh_tokens (user_id);
CREATE INDEX ix_auth_refresh_tokens_expires_at ON auth_refresh_tokens (expires_at);
