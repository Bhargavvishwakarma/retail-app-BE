package com.retail.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.enums.NotificationType;

import java.util.UUID;

/** A concrete, recipient-owned notification ready to be persisted. */
public record NotificationCommand(
        UUID recipientUserId,
        UUID eventId,
        NotificationType type,
        NotificationCategory category,
        String title,
        String message,
        NotificationSeverity severity,
        String entityType,
        UUID entityId,
        JsonNode metadata,
        String deduplicationKey
) {
}
