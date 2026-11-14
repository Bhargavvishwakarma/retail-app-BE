package com.retail.service;

import com.retail.repository.DashboardSummaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAnalyticsServiceTest {
    @Mock
    private DashboardSummaryRepository dashboardRepository;

    @InjectMocks
    private DashboardAnalyticsService service;

    @Test
    void comparesEqualPeriodsAndFillsDaysWithoutSales() {
        when(dashboardRepository.loadDailySales(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(daily("2026-11-12", "800.00", 2), daily("2026-11-14", "1250.00", 3)));
        when(dashboardRepository.loadInventoryAnalytics(anyInt(), anyInt())).thenReturn(List.of(inventory()));
        when(dashboardRepository.findTopProducts(any(), any())).thenReturn(List.of());
        when(dashboardRepository.loadPayments(any(), any())).thenReturn(List.of());
        when(dashboardRepository.findSlowMovingProducts(any(), anyInt())).thenReturn(List.of());
        when(dashboardRepository.findRecentActivities()).thenReturn(List.of());

        var response = service.getAnalytics(LocalDate.parse("2026-11-14"), LocalDate.parse("2026-11-15"));

        assertEquals(new BigDecimal("1250.00"), response.current().revenue());
        assertEquals(3, response.current().transactions());
        assertEquals(new BigDecimal("800.00"), response.previous().revenue());
        assertEquals(2, response.previous().transactions());
        assertEquals(19, response.totalCustomers());
        assertEquals(2, response.dailySales().size());
        assertEquals(new BigDecimal("1250.00"), response.dailySales().get(0).revenue());
        assertEquals(BigDecimal.ZERO, response.dailySales().get(1).revenue());
        assertEquals(LocalDate.parse("2026-11-12"), response.previousDailySales().get(0).date());
        verify(dashboardRepository).loadDailySales(LocalDateTime.parse("2026-11-12T00:00:00"),
                LocalDateTime.parse("2026-11-16T00:00:00"));
    }

    @Test
    void rejectsInvalidAndOverlongRangesBeforeQuerying() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getAnalytics(LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-14")));
        assertThrows(IllegalArgumentException.class,
                () -> service.getAnalytics(LocalDate.parse("2025-01-01"), LocalDate.parse("2026-11-14")));
        verifyNoInteractions(dashboardRepository);
    }

    private DashboardSummaryRepository.DailySalesProjection daily(String date, String revenue, long transactions) {
        return new DashboardSummaryRepository.DailySalesProjection() {
            @Override public LocalDate getBusinessDate() { return LocalDate.parse(date); }
            @Override public BigDecimal getRevenue() { return new BigDecimal(revenue); }
            @Override public long getTransactions() { return transactions; }
        };
    }

    private DashboardSummaryRepository.InventoryAnalyticsProjection inventory() {
        return new DashboardSummaryRepository.InventoryAnalyticsProjection() {
            @Override public long getTotalProducts() { return 21; }
            @Override public long getLowStockProducts() { return 2; }
            @Override public long getOutOfStockProducts() { return 1; }
            @Override public BigDecimal getInventoryCostValue() { return new BigDecimal("50000.00"); }
            @Override public long getTotalCustomers() { return 19; }
            @Override public java.util.UUID getId() { return null; }
            @Override public String getName() { return null; }
            @Override public String getSku() { return null; }
            @Override public Integer getQuantity() { return null; }
            @Override public Integer getReorderThreshold() { return null; }
        };
    }
}
