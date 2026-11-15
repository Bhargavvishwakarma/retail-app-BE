package com.retail.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.enums.NotificationType;

import java.util.UUID;

/** A notification selected by a business rule before recipients are resolved. */
public record NotificationDefinition(
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String message,
        String entityType,
        UUID entityId,
        JsonNode metadata
) {
}
