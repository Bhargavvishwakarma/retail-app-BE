package com.retail.notification.service;

import com.retail.dto.PageResponse;
import com.retail.model.AppUser;
import com.retail.notification.dto.NotificationCountResponse;
import com.retail.notification.dto.NotificationDelivery;
import com.retail.notification.dto.NotificationResponse;
import com.retail.notification.entity.Notification;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.repository.NotificationRepository;
import com.retail.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persists and retrieves recipient-owned notifications. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String INSERT_NOTIFICATION = """
            insert into notifications (
                id, event_id, recipient_user_id, type, category, title, message, severity,
                entity_type, entity_id, metadata, deduplication_key, is_read, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, false, ?)
            on conflict (recipient_user_id, deduplication_key)
                where deduplication_key is not null do nothing
            returning id
            """;

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;

    public PageResponse<NotificationResponse> history(String authenticatedEmail, int page, int size,
                                                       NotificationCategory category,
                                                       NotificationSeverity severity, Boolean isRead) {
        UUID recipientUserId = currentUserId(authenticatedEmail);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<NotificationResponse> notificationPage = notificationRepository
                .findVisibleForRecipient(recipientUserId, category, severity, isRead, pageable)
                .map(NotificationResponse::from);
        return PageResponse.from(notificationPage);
    }

    public NotificationCountResponse unreadCount(String authenticatedEmail) {
        UUID recipientUserId = currentUserId(authenticatedEmail);
        return new NotificationCountResponse(
                notificationRepository.countByRecipientUserIdAndReadFalseAndDeletedAtIsNull(recipientUserId));
    }

    @Transactional
    public NotificationResponse markRead(String authenticatedEmail, UUID notificationId) {
        Notification notification = visibleNotification(currentUserId(authenticatedEmail), notificationId);
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public NotificationCountResponse markAllRead(String authenticatedEmail) {
        int changed = notificationRepository.markAllReadForRecipient(currentUserId(authenticatedEmail), Instant.now());
        return new NotificationCountResponse(changed);
    }

    @Transactional
    public void delete(String authenticatedEmail, UUID notificationId) {
        Notification notification = visibleNotification(currentUserId(authenticatedEmail), notificationId);
        notification.setDeletedAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public NotificationCountResponse deleteRead(String authenticatedEmail) {
        int changed = notificationRepository.softDeleteReadForRecipient(currentUserId(authenticatedEmail), Instant.now());
        return new NotificationCountResponse(changed);
    }

    /** Resolves the authenticated account again rather than trusting any client-provided user id. */
    public UUID currentUserId(String authenticatedEmail) {
        return appUserRepository.findByEmailIgnoreCase(authenticatedEmail)
                .filter(AppUser::isActive)
                .map(AppUser::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authentication is required"));
    }

    /**
     * Inserts deliveries with PostgreSQL's partial unique index as the final
     * idempotency guard. Only rows inserted by this attempt are returned for SSE.
     */
    @Transactional
    public List<NotificationDelivery> persist(List<NotificationCommand> commands) {
        if (commands.isEmpty()) {
            return List.of();
        }

        Instant createdAt = Instant.now();
        List<NotificationDelivery> inserted = new ArrayList<>();
        for (NotificationCommand command : commands) {
            UUID notificationId = UUID.randomUUID();
            List<UUID> insertedIds = insertNotification(notificationId, command, createdAt);
            if (!insertedIds.isEmpty()) {
                inserted.add(new NotificationDelivery(
                        notificationId, command.recipientUserId(), command.eventId(), command.type(),
                        command.category(), command.title(), command.message(), command.severity(),
                        command.entityType(), command.entityId(), command.metadata(), createdAt));
            }
        }
        return List.copyOf(inserted);
    }

    private List<UUID> insertNotification(UUID notificationId, NotificationCommand command, Instant createdAt) {
        try {
            return jdbcTemplate.query(INSERT_NOTIFICATION, statement -> {
                statement.setObject(1, notificationId);
                statement.setObject(2, command.eventId());
                statement.setObject(3, command.recipientUserId());
                statement.setString(4, command.type().name());
                statement.setString(5, command.category().name());
                statement.setString(6, command.title());
                statement.setString(7, command.message());
                statement.setString(8, command.severity().name());
                statement.setString(9, command.entityType());
                statement.setObject(10, command.entityId());
                statement.setString(11, command.metadata() == null ? "{}" : command.metadata().toString());
                statement.setString(12, command.deduplicationKey());
                statement.setTimestamp(13, Timestamp.from(createdAt));
            }, (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class));
        } catch (DataAccessException exception) {
            // Let the caller's outbox transaction fail and schedule a retry. Internal
            // database details are logged by the processor and never reach clients.
            throw exception;
        }
    }

    private Notification visibleNotification(UUID recipientUserId, UUID notificationId) {
        return notificationRepository.findByIdAndRecipientUserIdAndDeletedAtIsNull(notificationId, recipientUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification was not found"));
    }
}
