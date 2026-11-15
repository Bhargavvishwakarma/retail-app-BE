package com.retail.notification.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.retail.config.SecurityConfig;
import com.retail.dto.PageResponse;
import com.retail.model.AppUser;
import com.retail.notification.dto.NotificationResponse;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.enums.NotificationType;
import com.retail.notification.realtime.NotificationStreamService;
import com.retail.notification.service.NotificationService;
import com.retail.repository.AppUserRepository;
import com.retail.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationService notificationService;
    @MockBean
    private NotificationStreamService streamService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private AppUserRepository appUserRepository;

    @Test
    void unauthenticatedHistoryIsRejected() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "desk@example.com", roles = "RECEPTIONIST")
    void receptionistCanReadOnlyTheirNotificationHistoryWithBoundedPaging() throws Exception {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse response = new NotificationResponse(notificationId, UUID.randomUUID(),
                NotificationType.LOW_STOCK, NotificationCategory.INVENTORY, "Low stock", "Only 5 remain",
                NotificationSeverity.WARNING, "PRODUCT", UUID.randomUUID(), JsonNodeFactory.instance.objectNode(),
                false, null, Instant.now());
        PageResponse<NotificationResponse> page = new PageResponse<>(List.of(response), 0, 100,
                1, 1, true, true, 1, false);
        when(notificationService.history(eq("desk@example.com"), eq(0), eq(100), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mvc.perform(get("/api/notifications").param("page", "-5").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.size").value(100));

        verify(notificationService).history(eq("desk@example.com"), eq(0), eq(100), isNull(), isNull(), isNull());
    }

    @Test
    @WithMockUser(username = "desk@example.com", roles = "RECEPTIONIST")
    void markReadUsesTheAuthenticatedIdentityInsteadOfAnyClientSuppliedUserId() throws Exception {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse response = new NotificationResponse(notificationId, UUID.randomUUID(),
                NotificationType.SALE_COMPLETED, NotificationCategory.SALES, "Sale completed", "Done",
                NotificationSeverity.SUCCESS, "SALE", UUID.randomUUID(), JsonNodeFactory.instance.objectNode(),
                true, Instant.now(), Instant.now());
        when(notificationService.markRead("desk@example.com", notificationId)).thenReturn(response);

        mvc.perform(patch("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        verify(notificationService).markRead("desk@example.com", notificationId);
    }
}
