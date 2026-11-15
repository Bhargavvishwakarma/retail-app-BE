package com.retail.notification.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationStreamServiceTest {

    @Test
    void tracksMultipleTabsAndCleansThemUpAtShutdown() {
        NotificationStreamService streams = new NotificationStreamService(new ObjectMapper(), 60_000);
        UUID recipientUserId = UUID.randomUUID();

        SseEmitter first = streams.connect(recipientUserId, null);
        SseEmitter second = streams.connect(recipientUserId, "prior-notification-id");

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(2, streams.activeConnectionCount(recipientUserId));

        streams.closeStreams();

        assertEquals(0, streams.activeConnectionCount(recipientUserId));
    }

    @Test
    void publishesToAllOpenTabsWithoutTreatingTheRegistryAsPersistence() {
        NotificationStreamService streams = new NotificationStreamService(new ObjectMapper(), 60_000);
        UUID recipientUserId = UUID.randomUUID();
        streams.connect(recipientUserId, null);
        streams.connect(recipientUserId, null);

        streams.publish(recipientUserId, java.util.Map.of("title", "Low stock"), UUID.randomUUID().toString());

        assertEquals(2, streams.activeConnectionCount(recipientUserId));
        streams.closeStreams();
    }
}
