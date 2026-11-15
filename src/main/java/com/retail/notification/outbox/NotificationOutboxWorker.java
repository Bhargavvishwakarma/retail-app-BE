package com.retail.notification.outbox;

import com.retail.notification.dto.NotificationDelivery;
import com.retail.notification.dto.NotificationResponse;
import com.retail.notification.entity.NotificationOutbox;
import com.retail.notification.enums.NotificationOutboxStatus;
import com.retail.notification.realtime.NotificationStreamService;
import com.retail.notification.repository.NotificationOutboxRepository;
import com.retail.notification.service.NotificationCommand;
import com.retail.notification.service.NotificationDefinition;
import com.retail.notification.service.NotificationRecipientService;
import com.retail.notification.service.NotificationRuleHandler;
import com.retail.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Transactional worker operations invoked by the scheduled outbox processor. */
@Service
@Slf4j
public class NotificationOutboxWorker {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRuleHandler ruleHandler;
    private final NotificationRecipientService recipientService;
    private final NotificationService notificationService;
    private final NotificationStreamService streamService;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration processingLease;

    public NotificationOutboxWorker(NotificationOutboxRepository outboxRepository,
                                    NotificationRuleHandler ruleHandler,
                                    NotificationRecipientService recipientService,
                                    NotificationService notificationService,
                                    NotificationStreamService streamService,
                                    @Value("${app.notifications.outbox.batch-size:50}") int batchSize,
                                    @Value("${app.notifications.outbox.max-attempts:5}") int maxAttempts,
                                    @Value("${app.notifications.outbox.processing-lease-seconds:300}") long processingLeaseSeconds) {
        if (batchSize < 1) throw new IllegalArgumentException("Notification outbox batch size must be positive");
        if (maxAttempts < 1) throw new IllegalArgumentException("Notification outbox max attempts must be positive");
        if (processingLeaseSeconds < 1) {
            throw new IllegalArgumentException("Notification outbox processing lease must be positive");
        }
        this.outboxRepository = outboxRepository;
        this.ruleHandler = ruleHandler;
        this.recipientService = recipientService;
        this.notificationService = notificationService;
        this.streamService = streamService;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.processingLease = Duration.ofSeconds(processingLeaseSeconds);
    }

    /** Claims a bounded batch using PostgreSQL row locks, then commits the lease quickly. */
    @Transactional
    public List<UUID> claimReadyEvents() {
        Instant now = Instant.now();
        recoverStaleLeases(now);
        outboxRepository.flush();

        List<NotificationOutbox> ready = outboxRepository.lockReadyBatch(batchSize);
        for (NotificationOutbox outbox : ready) {
            outbox.setStatus(NotificationOutboxStatus.PROCESSING);
            outbox.setAttempts(outbox.getAttempts() + 1);
            outbox.setProcessingStartedAt(now);
            outbox.setLastError(null);
        }
        outboxRepository.flush();
        return ready.stream().map(NotificationOutbox::getId).toList();
    }

    /** Creates notification records and marks the event done in one transaction. */
    @Transactional
    public void processClaimedEvent(UUID eventId) {
        Optional<NotificationOutbox> maybeOutbox = outboxRepository
                .findByIdAndStatus(eventId, NotificationOutboxStatus.PROCESSING);
        if (maybeOutbox.isEmpty()) {
            return;
        }

        NotificationOutbox outbox = maybeOutbox.get();
        List<NotificationCommand> commands = commandsFor(outbox);
        List<NotificationDelivery> deliveries = notificationService.persist(commands);

        Instant processedAt = Instant.now();
        outbox.setStatus(NotificationOutboxStatus.PROCESSED);
        outbox.setProcessedAt(processedAt);
        outbox.setProcessingStartedAt(null);
        // nextAttemptAt is non-null by schema and is ignored for terminal rows.
        outbox.setNextAttemptAt(processedAt);
        outbox.setLastError(null);
        outboxRepository.save(outbox);

        publishAfterCommit(deliveries);
        log.debug("notification_outbox_processed eventId={} eventType={} deliveries={}",
                outbox.getId(), outbox.getEventType(), deliveries.size());
    }

    /** Releases a failed lease with capped exponential backoff, or permanently fails it. */
    @Transactional
    public void recordFailure(UUID eventId, RuntimeException exception) {
        Optional<NotificationOutbox> maybeOutbox = outboxRepository
                .findByIdAndStatus(eventId, NotificationOutboxStatus.PROCESSING);
        if (maybeOutbox.isEmpty()) {
            return;
        }

        NotificationOutbox outbox = maybeOutbox.get();
        Instant now = Instant.now();
        outbox.setProcessingStartedAt(null);
        outbox.setLastError(errorSummary(exception));
        if (outbox.getAttempts() >= maxAttempts) {
            outbox.setStatus(NotificationOutboxStatus.FAILED);
            outbox.setNextAttemptAt(now);
            log.error("notification_outbox_failed eventId={} eventType={} attempts={} errorType={}",
                    outbox.getId(), outbox.getEventType(), outbox.getAttempts(),
                    exception.getClass().getSimpleName());
        } else {
            outbox.setStatus(NotificationOutboxStatus.PENDING);
            outbox.setNextAttemptAt(now.plusSeconds(backoffSeconds(outbox.getAttempts())));
            log.warn("notification_outbox_retry_scheduled eventId={} eventType={} attempts={} errorType={}",
                    outbox.getId(), outbox.getEventType(), outbox.getAttempts(),
                    exception.getClass().getSimpleName());
        }
        outboxRepository.save(outbox);
    }

    private void recoverStaleLeases(Instant now) {
        List<NotificationOutbox> stale = outboxRepository.lockStaleProcessing(now.minus(processingLease), batchSize);
        for (NotificationOutbox outbox : stale) {
            outbox.setProcessingStartedAt(null);
            outbox.setLastError("Processing lease expired");
            if (outbox.getAttempts() >= maxAttempts) {
                outbox.setStatus(NotificationOutboxStatus.FAILED);
                outbox.setNextAttemptAt(now);
                log.error("notification_outbox_stale_failed eventId={} eventType={} attempts={}",
                        outbox.getId(), outbox.getEventType(), outbox.getAttempts());
            } else {
                outbox.setStatus(NotificationOutboxStatus.PENDING);
                outbox.setNextAttemptAt(now);
                log.warn("notification_outbox_stale_recovered eventId={} eventType={} attempts={}",
                        outbox.getId(), outbox.getEventType(), outbox.getAttempts());
            }
        }
    }

    private List<NotificationCommand> commandsFor(NotificationOutbox outbox) {
        List<NotificationCommand> commands = new ArrayList<>();
        for (NotificationDefinition definition : ruleHandler.resolve(outbox)) {
            String deduplicationKey = outbox.getId() + ":" + definition.type().name();
            for (UUID recipientUserId : recipientService.resolveRecipients(definition.type())) {
                commands.add(new NotificationCommand(
                        recipientUserId, outbox.getId(), definition.type(), definition.type().category(),
                        definition.title(), definition.message(), definition.severity(), definition.entityType(),
                        definition.entityId(), definition.metadata(), deduplicationKey));
            }
        }
        return commands;
    }

    private void publishAfterCommit(List<NotificationDelivery> deliveries) {
        if (deliveries.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (NotificationDelivery delivery : deliveries) {
                    try {
                        streamService.publish(delivery.recipientUserId(), NotificationResponse.from(delivery),
                                delivery.id().toString());
                    } catch (RuntimeException exception) {
                        // SSE is best-effort; persistence is already safely committed.
                        log.warn("notification_sse_publish_failed notificationId={} recipientUserId={} errorType={}",
                                delivery.id(), delivery.recipientUserId(), exception.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    private long backoffSeconds(int attempts) {
        int exponent = Math.min(10, Math.max(0, attempts - 1));
        return Math.min(300L, 1L << exponent);
    }

    private String errorSummary(RuntimeException exception) {
        String message = exception.getMessage();
        String value = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 1_000 ? value : value.substring(0, 1_000);
    }
}
