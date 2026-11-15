package com.retail.notification.service;

import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationType;
import com.retail.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Resolves recipients from server-side roles and active accounts only. */
@Service
@RequiredArgsConstructor
public class NotificationRecipientService {

    private final AppUserRepository appUserRepository;
    private final NotificationPreferenceService preferenceService;

    public List<UUID> resolveRecipients(NotificationType notificationType) {
        List<UUID> authorizedUsers = appUserRepository.findByActiveTrue().stream()
                .filter(user -> mayReceive(user, notificationType))
                .map(AppUser::getId)
                .toList();
        return preferenceService.enabledRecipients(authorizedUsers, notificationType);
    }

    private boolean mayReceive(AppUser user, NotificationType notificationType) {
        if (user.getRole() == UserRole.OWNER) {
            return true;
        }
        if (user.getRole() != UserRole.RECEPTIONIST) {
            return false;
        }

        // Receptionists receive operational inventory, sales, purchase, and
        // customer activity only. Financial and administrative details remain
        // owner-only even if a future workflow emits one of those types.
        if (notificationType == NotificationType.HIGH_VALUE_SALE
                || notificationType == NotificationType.SUPPLIER_PAYMENT_DUE
                || notificationType == NotificationType.SUPPLIER_PAYMENT_COMPLETED
                || notificationType == NotificationType.CUSTOMER_BALANCE_DUE
                || notificationType == NotificationType.EMPLOYEE_ACTIVITY) {
            return false;
        }
        return notificationType.category() == NotificationCategory.INVENTORY
                || notificationType.category() == NotificationCategory.SALES
                || notificationType.category() == NotificationCategory.PURCHASE
                || notificationType.category() == NotificationCategory.CUSTOMER;
    }
}
