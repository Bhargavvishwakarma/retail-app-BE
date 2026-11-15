package com.retail.notification.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

/** Internal representation of a notification inserted during outbox processing. */
public record NotificationDelivery(
        UUID id,
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
        Instant createdAt
) {
}
