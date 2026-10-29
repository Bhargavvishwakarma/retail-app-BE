ALTER TABLE app_users
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN google_sub VARCHAR(255),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_app_users_google_sub
    ON app_users (google_sub)
    WHERE google_sub IS NOT NULL;

-- Existing users are preserved. This also prevents concurrent registration
-- using differently-cased variants of the same email address.
CREATE UNIQUE INDEX uq_app_users_email_lower
    ON app_users (LOWER(email));
