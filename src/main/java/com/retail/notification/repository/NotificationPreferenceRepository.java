package com.retail.notification.repository;

import com.retail.notification.entity.NotificationPreference;
import com.retail.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserIdAndNotificationType(UUID userId, NotificationType notificationType);

    List<NotificationPreference> findByUserIdInAndNotificationType(Collection<UUID> userIds,
                                                                    NotificationType notificationType);
}
