package com.retail.notification.event;

import com.retail.notification.outbox.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Inserts the outbox row before the originating transaction commits. If outbox
 * persistence fails, the original business transaction is rolled back as well.
 */
@Component
@RequiredArgsConstructor
public class NotificationBusinessEventListener {

    private final NotificationOutboxService notificationOutboxService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBusinessEvent(NotificationBusinessEvent event) {
        notificationOutboxService.capture(event);
    }
}
