package com.retail.notification.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory delivery registry for persisted notification events.
 *
 * <p>This is deliberately not a source of truth: reconnecting clients use the
 * notification-history API to reconcile anything sent while disconnected. The
 * caller must invoke {@link #publish(UUID, Object, String)} only after the
 * notification transaction commits.</p>
 */
@Service
@Slf4j
public class NotificationStreamService {

    private final ObjectMapper objectMapper;
    private final long emitterTimeoutMs;
    private final ConcurrentMap<UUID, ConcurrentMap<String, SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public NotificationStreamService(ObjectMapper objectMapper,
                                     @Value("${app.notifications.sse-timeout-ms:1800000}") long emitterTimeoutMs) {
        this.objectMapper = objectMapper;
        if (emitterTimeoutMs <= 0) {
            throw new IllegalArgumentException("Notification SSE timeout must be positive");
        }
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    /**
     * Creates one stream for the already-authenticated recipient. A user may
     * have many active streams (for example, one for each browser tab).
     */
    public SseEmitter connect(UUID recipientUserId, String lastEventId) {
        Objects.requireNonNull(recipientUserId, "recipientUserId");

        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        emittersByUser.compute(recipientUserId, (ignored, connections) -> {
            ConcurrentMap<String, SseEmitter> activeConnections = connections == null
                    ? new ConcurrentHashMap<>() : connections;
            activeConnections.put(connectionId, emitter);
            return activeConnections;
        });

        emitter.onCompletion(() -> remove(recipientUserId, connectionId));
        emitter.onTimeout(() -> remove(recipientUserId, connectionId));
        emitter.onError(error -> remove(recipientUserId, connectionId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(3_000)
                    .data(objectMapper.valueToTree(Map.of(
                            "lastEventId", lastEventId == null ? "" : lastEventId,
                            "connectedAt", Instant.now().toString())), MediaType.APPLICATION_JSON));
            log.debug("notification_sse_connected userId={} connectionId={}", recipientUserId, connectionId);
        } catch (IOException | RuntimeException exception) {
            discard(recipientUserId, connectionId, emitter, exception);
        }
        return emitter;
    }

    /**
     * Sends an already-persisted notification to every open stream belonging
     * to the recipient. The event id is the persisted notification UUID, so a
     * client can send it back as {@code Last-Event-ID} when reconnecting.
     */
    public void publish(UUID recipientUserId, Object payload, String eventId) {
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(eventId, "eventId");

        ConcurrentMap<String, SseEmitter> connections = emittersByUser.get(recipientUserId);
        if (connections == null || connections.isEmpty()) {
            return;
        }

        for (Map.Entry<String, SseEmitter> connection : connections.entrySet()) {
            try {
                connection.getValue().send(SseEmitter.event()
                        .id(eventId)
                        .name("notification")
                        .data(objectMapper.valueToTree(payload), MediaType.APPLICATION_JSON));
            } catch (IOException | RuntimeException exception) {
                discard(recipientUserId, connection.getKey(), connection.getValue(), exception);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.notifications.sse-heartbeat-ms:25000}")
    public void sendHeartbeats() {
        if (emittersByUser.isEmpty()) {
            return;
        }

        String timestamp = Instant.now().toString();
        for (Map.Entry<UUID, ConcurrentMap<String, SseEmitter>> userConnections : emittersByUser.entrySet()) {
            for (Map.Entry<String, SseEmitter> connection : userConnections.getValue().entrySet()) {
                try {
                    connection.getValue().send(SseEmitter.event()
                            .name("heartbeat")
                            .data(objectMapper.valueToTree(Map.of("timestamp", timestamp)), MediaType.APPLICATION_JSON));
                } catch (IOException | RuntimeException exception) {
                    discard(userConnections.getKey(), connection.getKey(), connection.getValue(), exception);
                }
            }
        }
    }

    /** Exposed for focused lifecycle tests and operational diagnostics. */
    public int activeConnectionCount(UUID recipientUserId) {
        ConcurrentMap<String, SseEmitter> connections = emittersByUser.get(recipientUserId);
        return connections == null ? 0 : connections.size();
    }

    @PreDestroy
    public void closeStreams() {
        for (ConcurrentMap<String, SseEmitter> connections : emittersByUser.values()) {
            for (SseEmitter emitter : connections.values()) {
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // The client can already have disconnected while the app is stopping.
                }
            }
        }
        emittersByUser.clear();
    }

    private void discard(UUID recipientUserId, String connectionId, SseEmitter emitter, Exception exception) {
        remove(recipientUserId, connectionId);
        try {
            emitter.completeWithError(exception);
        } catch (RuntimeException ignored) {
            // Completion is best effort; removal above is the resource-safety guarantee.
        }
        log.debug("notification_sse_disconnected userId={} connectionId={} reason={}",
                recipientUserId, connectionId, exception.getClass().getSimpleName());
    }

    private void remove(UUID recipientUserId, String connectionId) {
        // Both add and remove mutate the per-user map inside compute, avoiding a
        // race that could orphan a freshly opened connection while pruning an
        // empty user bucket.
        emittersByUser.computeIfPresent(recipientUserId, (ignored, connections) -> {
            connections.remove(connectionId);
            return connections.isEmpty() ? null : connections;
        });
    }
}
