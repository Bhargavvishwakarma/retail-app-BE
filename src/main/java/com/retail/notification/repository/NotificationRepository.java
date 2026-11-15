package com.retail.notification.repository;

import com.retail.notification.entity.Notification;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            where n.recipientUserId = :recipientUserId
              and n.deletedAt is null
              and (:category is null or n.category = :category)
              and (:severity is null or n.severity = :severity)
              and (:isRead is null or n.read = :isRead)
            """)
    Page<Notification> findVisibleForRecipient(@Param("recipientUserId") UUID recipientUserId,
                                               @Param("category") NotificationCategory category,
                                               @Param("severity") NotificationSeverity severity,
                                               @Param("isRead") Boolean isRead,
                                               Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserIdAndDeletedAtIsNull(UUID id, UUID recipientUserId);

    long countByRecipientUserIdAndReadFalseAndDeletedAtIsNull(UUID recipientUserId);

    @Modifying
    @Query("""
            update Notification n
            set n.read = true, n.readAt = :readAt
            where n.recipientUserId = :recipientUserId
              and n.deletedAt is null
              and n.read = false
            """)
    int markAllReadForRecipient(@Param("recipientUserId") UUID recipientUserId,
                                @Param("readAt") Instant readAt);

    @Modifying
    @Query("""
            update Notification n
            set n.deletedAt = :deletedAt
            where n.recipientUserId = :recipientUserId
              and n.deletedAt is null
              and n.read = true
            """)
    int softDeleteReadForRecipient(@Param("recipientUserId") UUID recipientUserId,
                                   @Param("deletedAt") Instant deletedAt);
}
