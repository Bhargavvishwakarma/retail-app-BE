package com.retail.notification.event;

import java.util.UUID;

/**
 * Stable contract emitted by business modules. The notification module persists these
 * contracts to the transactional outbox before a transaction commits.
 */
public interface NotificationBusinessEvent {

    UUID eventId();

    String eventType();

    String aggregateType();

    UUID aggregateId();
}
