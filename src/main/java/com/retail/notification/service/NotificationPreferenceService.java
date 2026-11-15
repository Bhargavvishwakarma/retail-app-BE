package com.retail.notification.service;

import com.retail.notification.entity.NotificationPreference;
import com.retail.notification.enums.NotificationType;
import com.retail.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Preferences are opt-out only in Release 1. Missing rows intentionally mean
 * enabled so newly authorized users receive supported notifications by default.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public List<UUID> enabledRecipients(Collection<UUID> userIds, NotificationType notificationType) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Boolean> enabledByUser = new HashMap<>();
        for (NotificationPreference preference :
                preferenceRepository.findByUserIdInAndNotificationType(userIds, notificationType)) {
            enabledByUser.put(preference.getUserId(), preference.isEnabled());
        }

        return userIds.stream()
                .filter(userId -> enabledByUser.getOrDefault(userId, true))
                .distinct()
                .toList();
    }
}
