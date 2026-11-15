package com.retail.notification.service;

import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.notification.entity.Notification;
import com.retail.notification.repository.NotificationRepository;
import com.retail.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceOwnershipTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void markReadScopesLookupToAuthenticatedUsersNotification() {
        UUID ownerId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        AppUser owner = activeUser(ownerId, "owner@example.com");
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setRecipientUserId(ownerId);

        when(appUserRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(notificationRepository.findByIdAndRecipientUserIdAndDeletedAtIsNull(notificationId, ownerId))
                .thenReturn(Optional.of(notification));

        notificationService.markRead("owner@example.com", notificationId);

        assertEquals(true, notification.isRead());
        verify(notificationRepository).findByIdAndRecipientUserIdAndDeletedAtIsNull(notificationId, ownerId);
    }

    @Test
    void anotherUsersNotificationLooksNotFoundInsteadOfBeingExposed() {
        UUID receptionistId = UUID.randomUUID();
        UUID someoneElsesNotification = UUID.randomUUID();
        when(appUserRepository.findByEmailIgnoreCase("desk@example.com"))
                .thenReturn(Optional.of(activeUser(receptionistId, "desk@example.com")));
        when(notificationRepository.findByIdAndRecipientUserIdAndDeletedAtIsNull(
                someoneElsesNotification, receptionistId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> notificationService.markRead("desk@example.com", someoneElsesNotification));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(notificationRepository).findByIdAndRecipientUserIdAndDeletedAtIsNull(
                someoneElsesNotification, receptionistId);
    }

    @Test
    void deleteSoftDeletesOnlyTheAuthenticatedUsersVisibleNotification() {
        UUID ownerId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setRecipientUserId(ownerId);
        when(appUserRepository.findByEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.of(activeUser(ownerId, "owner@example.com")));
        when(notificationRepository.findByIdAndRecipientUserIdAndDeletedAtIsNull(notificationId, ownerId))
                .thenReturn(Optional.of(notification));

        notificationService.delete("owner@example.com", notificationId);

        assertEquals(true, notification.getDeletedAt() != null);
        verify(notificationRepository).save(notification);
    }

    private AppUser activeUser(UUID id, String email) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setRole(UserRole.OWNER);
        user.setActive(true);
        return user;
    }
}
