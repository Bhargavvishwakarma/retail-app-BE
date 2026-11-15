package com.retail.notification.enums;

/** Lifecycle state for an event captured in the transactional notification outbox. */
public enum NotificationOutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
