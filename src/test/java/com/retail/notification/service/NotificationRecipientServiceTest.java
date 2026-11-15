package com.retail.notification.service;

import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.notification.enums.NotificationType;
import com.retail.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private NotificationPreferenceService preferenceService;

    @InjectMocks
    private NotificationRecipientService recipientService;

    @Test
    void inventoryNotificationsGoToOwnersAndReceptionists() {
        AppUser owner = user(UserRole.OWNER);
        AppUser receptionist = user(UserRole.RECEPTIONIST);
        stubActiveUsers(owner, receptionist);

        List<UUID> recipients = recipientService.resolveRecipients(NotificationType.LOW_STOCK);

        assertEquals(List.of(owner.getId(), receptionist.getId()), recipients);
    }

    @Test
    void highValueSaleGoesToOwnersOnly() {
        AppUser owner = user(UserRole.OWNER);
        AppUser receptionist = user(UserRole.RECEPTIONIST);
        stubActiveUsers(owner, receptionist);

        List<UUID> recipients = recipientService.resolveRecipients(NotificationType.HIGH_VALUE_SALE);

        assertEquals(List.of(owner.getId()), recipients);
    }

    @Test
    void paymentInformationDoesNotGoToReceptionists() {
        AppUser owner = user(UserRole.OWNER);
        AppUser receptionist = user(UserRole.RECEPTIONIST);
        stubActiveUsers(owner, receptionist);

        List<UUID> recipients = recipientService.resolveRecipients(NotificationType.SUPPLIER_PAYMENT_DUE);

        assertEquals(List.of(owner.getId()), recipients);
    }

    private void stubActiveUsers(AppUser... users) {
        when(appUserRepository.findByActiveTrue()).thenReturn(List.of(users));
        when(preferenceService.enabledRecipients(anyList(), any(NotificationType.class)))
                .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
    }

    private AppUser user(UserRole role) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
