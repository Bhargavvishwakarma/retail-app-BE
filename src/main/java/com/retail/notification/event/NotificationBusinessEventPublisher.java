package com.retail.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The only notification-module dependency exposed to existing business services.
 * It deliberately publishes contracts rather than letting business services know about
 * outbox persistence, recipient rules, or real-time delivery.
 */
@Component
@RequiredArgsConstructor
public class NotificationBusinessEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(NotificationBusinessEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
