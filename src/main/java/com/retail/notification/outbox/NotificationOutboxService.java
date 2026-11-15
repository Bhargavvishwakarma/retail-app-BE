package com.retail.notification.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.notification.entity.NotificationOutbox;
import com.retail.notification.enums.NotificationOutboxStatus;
import com.retail.notification.event.NotificationBusinessEvent;
import com.retail.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Captures a business event in the same database transaction as its aggregate change. */
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void capture(NotificationBusinessEvent event) {
        // A producer retries with the same event id only after its original transaction
        // committed. Keeping the event id as the primary key makes that retry idempotent.
        if (outboxRepository.existsById(event.eventId())) {
            return;
        }

        Instant now = Instant.now();
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setId(event.eventId());
        outbox.setEventType(event.eventType());
        outbox.setAggregateType(event.aggregateType());
        outbox.setAggregateId(event.aggregateId());
        outbox.setPayload(objectMapper.valueToTree(event));
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);
    }
}
