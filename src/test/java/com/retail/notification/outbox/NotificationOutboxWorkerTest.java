package com.retail.notification.outbox;

import com.retail.notification.enums.NotificationOutboxStatus;
import com.retail.notification.entity.NotificationOutbox;
import com.retail.notification.realtime.NotificationStreamService;
import com.retail.notification.repository.NotificationOutboxRepository;
import com.retail.notification.service.NotificationRecipientService;
import com.retail.notification.service.NotificationRuleHandler;
import com.retail.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxWorkerTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private NotificationRuleHandler ruleHandler;
    @Mock
    private NotificationRecipientService recipientService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationStreamService streamService;

    private NotificationOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new NotificationOutboxWorker(outboxRepository, ruleHandler, recipientService,
                notificationService, streamService, 10, 3, 60);
    }

    @Test
    void processingFailureSchedulesExponentialRetryBeforeMaximumAttempts() {
        NotificationOutbox outbox = processingOutbox(1);
        when(outboxRepository.findByIdAndStatus(outbox.getId(), NotificationOutboxStatus.PROCESSING))
                .thenReturn(Optional.of(outbox));
        Instant before = Instant.now();

        worker.recordFailure(outbox.getId(), new IllegalStateException("database temporarily unavailable"));

        assertEquals(NotificationOutboxStatus.PENDING, outbox.getStatus());
        assertNotNull(outbox.getNextAttemptAt());
        assertEquals(true, outbox.getNextAttemptAt().isAfter(before));
        verify(outboxRepository).save(outbox);
    }

    @Test
    void processingFailureBecomesFailedAtMaximumAttempts() {
        NotificationOutbox outbox = processingOutbox(3);
        when(outboxRepository.findByIdAndStatus(outbox.getId(), NotificationOutboxStatus.PROCESSING))
                .thenReturn(Optional.of(outbox));

        worker.recordFailure(outbox.getId(), new IllegalStateException("unreadable payload"));

        assertEquals(NotificationOutboxStatus.FAILED, outbox.getStatus());
        verify(outboxRepository).save(outbox);
    }

    @Test
    void anAlreadyProcessedOrUnclaimedEventIsNotProcessedAgain() {
        UUID eventId = UUID.randomUUID();
        when(outboxRepository.findByIdAndStatus(eventId, NotificationOutboxStatus.PROCESSING))
                .thenReturn(Optional.empty());

        worker.processClaimedEvent(eventId);

        verifyNoInteractions(ruleHandler, recipientService, notificationService, streamService);
    }

    private NotificationOutbox processingOutbox(int attempts) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setEventType("SALE_COMPLETED");
        outbox.setStatus(NotificationOutboxStatus.PROCESSING);
        outbox.setAttempts(attempts);
        outbox.setProcessingStartedAt(Instant.now());
        return outbox;
    }
}
