CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL REFERENCES app_users(id),
    type VARCHAR(80) NOT NULL,
    category VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(40) NOT NULL,
    entity_type VARCHAR(80),
    entity_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    deduplication_key VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_read_timestamp
        CHECK ((is_read = FALSE AND read_at IS NULL) OR (is_read = TRUE AND read_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_notifications_recipient_deduplication_key
    ON notifications (recipient_user_id, deduplication_key)
    WHERE deduplication_key IS NOT NULL;

CREATE INDEX ix_notifications_recipient_history
    ON notifications (recipient_user_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_notifications_recipient_unread
    ON notifications (recipient_user_id, created_at DESC)
    WHERE deleted_at IS NULL AND is_read = FALSE;

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    notification_type VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_preferences_user_type UNIQUE (user_id, notification_type)
);

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT chk_notification_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT chk_notification_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_notification_outbox_ready
    ON notification_outbox (next_attempt_at ASC, created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX ix_notification_outbox_stale_processing
    ON notification_outbox (processing_started_at ASC)
    WHERE status = 'PROCESSING';
