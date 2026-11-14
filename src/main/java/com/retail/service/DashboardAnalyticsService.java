package com.retail.service;

import com.retail.dto.DashboardAnalyticsResponse;
import com.retail.config.CacheNames;
import com.retail.repository.DashboardSummaryRepository;
import com.retail.util.StoreTime;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAnalyticsService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int INVENTORY_ATTENTION_LIMIT = 8;
    private static final int MAX_RANGE_DAYS = 366;

    private final DashboardSummaryRepository dashboardRepository;

    @Cacheable(cacheNames = CacheNames.DASHBOARD_ANALYTICS,
            key = "#p0.toString() + ':' + #p1.toString()",
            condition = "#p0 != null and #p1 != null", sync = true)
    @Transactional(readOnly = true)
    public DashboardAnalyticsResponse getAnalytics(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("A valid start and end date are required");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("The dashboard date range cannot exceed 366 days");
        }

        LocalDate previousFrom = from.minusDays(days);
        LocalDateTime currentStart = from.atStartOfDay();
        LocalDateTime currentEnd = to.plusDays(1).atStartOfDay();
        LocalDateTime previousStart = previousFrom.atStartOfDay();

        var dailySales = dashboardRepository.loadDailySales(previousStart, currentEnd);
        Map<LocalDate, DashboardSummaryRepository.DailySalesProjection> dailyByDate = dailySales.stream()
                        .collect(Collectors.toMap(DashboardSummaryRepository.DailySalesProjection::getBusinessDate,
                                row -> row));

        var currentMetrics = summarizePeriod(dailySales, from, to.plusDays(1));
        var previousMetrics = summarizePeriod(dailySales, previousFrom, from);
        var inventoryRows = dashboardRepository.loadInventoryAnalytics(
                LOW_STOCK_THRESHOLD, INVENTORY_ATTENTION_LIMIT);
        var inventory = inventoryRows.get(0);
        var inventorySummary = new DashboardAnalyticsResponse.InventorySummary(
                inventory.getTotalProducts(), inventory.getLowStockProducts(), inventory.getOutOfStockProducts(),
                inventory.getInventoryCostValue());
        var topProducts = dashboardRepository.findTopProducts(currentStart, currentEnd);

        return new DashboardAnalyticsResponse(
                from,
                to,
                java.time.ZonedDateTime.now(StoreTime.ZONE),
                currentMetrics,
                previousMetrics,
                inventory.getTotalCustomers(),
                toDailySeries(from, days, dailyByDate),
                toDailySeries(previousFrom, days, dailyByDate),
                topProducts.stream().filter(row -> "QUANTITY".equals(row.getRanking()))
                        .map(row -> new DashboardAnalyticsResponse.TopProduct(row.getName(), row.getSku(), row.getCategory(),
                                row.getUnitsSold(), row.getRevenue())).toList(),
                topProducts.stream().filter(row -> "REVENUE".equals(row.getRanking()))
                        .map(row -> new DashboardAnalyticsResponse.TopProduct(row.getName(), row.getSku(), row.getCategory(),
                                row.getUnitsSold(), row.getRevenue())).toList(),
                dashboardRepository.loadPayments(currentStart, currentEnd).stream()
                        .map(row -> new DashboardAnalyticsResponse.PaymentBreakdown(row.getMethod(), row.getAmount(), row.getTransactions()))
                        .toList(),
                inventorySummary,
                inventoryRows.stream().filter(row -> row.getId() != null)
                        .map(row -> new DashboardAnalyticsResponse.InventoryProduct(row.getId(), row.getName(), row.getSku(),
                                row.getQuantity(), row.getReorderThreshold())).toList(),
                dashboardRepository.findSlowMovingProducts(LocalDateTime.now(StoreTime.ZONE).minusDays(30), 5).stream()
                        .map(row -> new DashboardAnalyticsResponse.SlowMovingProduct(row.getId(), row.getName(), row.getSku(),
                                row.getCategory(), row.getQuantity())).toList(),
                dashboardRepository.findRecentActivities().stream()
                        .map(row -> new DashboardAnalyticsResponse.BusinessActivity(row.getId(), row.getType(), row.getTitle(),
                                row.getDetail(), row.getOccurredAt(), row.getAmount(), row.getPaymentMethod())).toList());
    }

    private DashboardAnalyticsResponse.PeriodMetrics summarizePeriod(
            List<DashboardSummaryRepository.DailySalesProjection> dailySales,
            LocalDate start,
            LocalDate endExclusive) {
        java.math.BigDecimal revenue = java.math.BigDecimal.ZERO;
        long transactions = 0;
        for (var day : dailySales) {
            if (!day.getBusinessDate().isBefore(start) && day.getBusinessDate().isBefore(endExclusive)) {
                revenue = revenue.add(day.getRevenue());
                transactions += day.getTransactions();
            }
        }
        return new DashboardAnalyticsResponse.PeriodMetrics(revenue, transactions);
    }

    private List<DashboardAnalyticsResponse.DailySales> toDailySeries(
            LocalDate start,
            long days,
            Map<LocalDate, DashboardSummaryRepository.DailySalesProjection> dailyByDate) {
        List<DashboardAnalyticsResponse.DailySales> series = new ArrayList<>((int) days);
        for (int day = 0; day < days; day++) {
            LocalDate date = start.plusDays(day);
            var values = dailyByDate.get(date);
            series.add(new DashboardAnalyticsResponse.DailySales(date,
                    values == null ? java.math.BigDecimal.ZERO : values.getRevenue(),
                    values == null ? 0 : values.getTransactions()));
        }
        return series;
    }
}
