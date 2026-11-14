package com.retail.controller;

import com.retail.config.SecurityConfig;
import com.retail.repository.AppUserRepository;
import com.retail.service.DashboardService;
import com.retail.service.DashboardAnalyticsService;
import com.retail.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardAuthorizationTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private DashboardAnalyticsService dashboardAnalyticsService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserRepository userRepository;

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCannotReadOwnerDashboardSummary() throws Exception {
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCanReadOperationalSummary() throws Exception {
        when(dashboardService.getOperationalSummary()).thenReturn(Map.of("totalProducts", 1));

        mvc.perform(get("/api/dashboard/operational")).andExpect(status().isOk());

        verify(dashboardService).getOperationalSummary();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCanReadOwnerDashboardSummary() throws Exception {
        when(dashboardService.getSummary()).thenReturn(Map.of("todaySalesCount", 1));

        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk());

        verify(dashboardService).getSummary();
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCannotReadOwnerDashboardAnalytics() throws Exception {
        mvc.perform(get("/api/dashboard/analytics")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-14"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardAnalyticsService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCanReadDateFilteredDashboardAnalytics() throws Exception {
        when(dashboardAnalyticsService.getAnalytics(
                java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-14")))
                .thenReturn(org.mockito.Mockito.mock(com.retail.dto.DashboardAnalyticsResponse.class));

        mvc.perform(get("/api/dashboard/analytics")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-14"))
                .andExpect(status().isOk());

        verify(dashboardAnalyticsService).getAnalytics(
                java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-14"));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void actuatorMetricsRemainOwnerOnly() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }
}
