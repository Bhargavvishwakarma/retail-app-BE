package com.retail.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        ZonedDateTime generatedAt,
        PeriodMetrics current,
        PeriodMetrics previous,
        long totalCustomers,
        List<DailySales> dailySales,
        List<DailySales> previousDailySales,
        List<TopProduct> topProductsByQuantity,
        List<TopProduct> topProductsByRevenue,
        List<PaymentBreakdown> payments,
        InventorySummary inventory,
        List<InventoryProduct> inventoryAttention,
        List<SlowMovingProduct> slowMovingProducts,
        List<BusinessActivity> recentActivity
) {
    public record PeriodMetrics(BigDecimal revenue, long transactions) {
    }

    public record DailySales(LocalDate date, BigDecimal revenue, long transactions) {
    }

    public record TopProduct(String name, String sku, String category, long unitsSold, BigDecimal revenue) {
    }

    public record PaymentBreakdown(String method, BigDecimal amount, long transactions) {
    }

    public record InventorySummary(long totalProducts, long lowStockProducts, long outOfStockProducts,
                                   BigDecimal inventoryCostValue) {
    }

    public record InventoryProduct(UUID id, String name, String sku, int quantity, int reorderThreshold) {
    }

    public record SlowMovingProduct(UUID id, String name, String sku, String category, int quantity) {
    }

    public record BusinessActivity(UUID id, String type, String title, String detail, LocalDateTime occurredAt,
                                   BigDecimal amount, String paymentMethod) {
    }
}
