package com.retail.notification.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Polls committed outbox rows asynchronously from product and sale request threads. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxProcessor {

    private final NotificationOutboxWorker worker;

    @Scheduled(fixedDelayString = "${app.notifications.outbox.poll-interval-ms:1000}")
    public void processPendingEvents() {
        final List<UUID> eventIds;
        try {
            eventIds = worker.claimReadyEvents();
        } catch (RuntimeException exception) {
            log.error("notification_outbox_claim_failed errorType={}", exception.getClass().getSimpleName(), exception);
            return;
        }

        for (UUID eventId : eventIds) {
            try {
                worker.processClaimedEvent(eventId);
            } catch (RuntimeException exception) {
                log.error("notification_outbox_processing_failed eventId={} errorType={}",
                        eventId, exception.getClass().getSimpleName(), exception);
                try {
                    worker.recordFailure(eventId, exception);
                } catch (RuntimeException recoveryException) {
                    log.error("notification_outbox_failure_recording_failed eventId={} errorType={}",
                            eventId, recoveryException.getClass().getSimpleName(), recoveryException);
                }
            }
        }
    }
}
