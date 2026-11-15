package com.retail.notification.repository;

import com.retail.notification.entity.NotificationOutbox;
import com.retail.notification.enums.NotificationOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    /**
     * Must be invoked in a short transaction. PostgreSQL's SKIP LOCKED lets
     * concurrently running application instances claim independent batches.
     */
    @Query(value = """
            select *
            from notification_outbox
            where status = 'PENDING'
              and next_attempt_at <= current_timestamp
            order by next_attempt_at asc, created_at asc
            for update skip locked
            limit :batchSize
            """, nativeQuery = true)
    List<NotificationOutbox> lockReadyBatch(@Param("batchSize") int batchSize);

    /**
     * Claims stale processing leases so a crashed worker cannot strand work.
     * Callers decide whether to retry or permanently fail each returned row.
     */
    @Query(value = """
            select *
            from notification_outbox
            where status = 'PROCESSING'
              and processing_started_at < :staleBefore
            order by processing_started_at asc
            for update skip locked
            limit :batchSize
            """, nativeQuery = true)
    List<NotificationOutbox> lockStaleProcessing(@Param("staleBefore") Instant staleBefore,
                                                 @Param("batchSize") int batchSize);

    Optional<NotificationOutbox> findByIdAndStatus(UUID id, NotificationOutboxStatus status);
}
