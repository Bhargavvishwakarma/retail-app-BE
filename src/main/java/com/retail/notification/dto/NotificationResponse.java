package com.retail.notification.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.retail.notification.entity.Notification;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

/** The persisted notification shape exposed through REST and SSE. */
public record NotificationResponse(
        UUID id,
        UUID eventId,
        NotificationType type,
        NotificationCategory category,
        String title,
        String message,
        NotificationSeverity severity,
        String entityType,
        UUID entityId,
        JsonNode metadata,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getEventId(), notification.getType(), notification.getCategory(),
                notification.getTitle(), notification.getMessage(), notification.getSeverity(), notification.getEntityType(),
                notification.getEntityId(), notification.getMetadata(), notification.isRead(), notification.getReadAt(),
                notification.getCreatedAt());
    }

    public static NotificationResponse from(NotificationDelivery delivery) {
        return new NotificationResponse(
                delivery.id(), delivery.eventId(), delivery.type(), delivery.category(), delivery.title(),
                delivery.message(), delivery.severity(), delivery.entityType(), delivery.entityId(), delivery.metadata(),
                false, null, delivery.createdAt());
    }
}
