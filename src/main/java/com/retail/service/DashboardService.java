package com.retail.service;

import com.retail.config.CacheNames;
import com.retail.dto.RecentSaleResponse;
import com.retail.repository.DashboardSummaryRepository;
import com.retail.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SaleRepository saleRepository;
    private final DashboardSummaryRepository dashboardSummaryRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.DASHBOARD_SUMMARY, key = "T(com.retail.util.StoreTime).today()")
    public Map<String, Object> getSummary() {
        LocalDate today = com.retail.util.StoreTime.today();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        var metrics = dashboardSummaryRepository.loadMetrics(start, end, 5);
        List<RecentSaleResponse> recentSales = saleRepository.findRecentSummaries(PageRequest.of(0, 5));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("todayRevenue", metrics.getTodayRevenue());
        summary.put("todaySalesCount", metrics.getTodaySalesCount());
        summary.put("totalProducts", metrics.getTotalProducts());
        summary.put("lowStockCount", metrics.getLowStockCount());
        summary.put("totalCustomers", metrics.getTotalCustomers());
        summary.put("recentSales", List.copyOf(recentSales));
        return Collections.unmodifiableMap(summary);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.DASHBOARD_OPERATIONAL, key = "T(com.retail.util.StoreTime).today()")
    public Map<String, Object> getOperationalSummary() {
        LocalDate today = com.retail.util.StoreTime.today();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        var metrics = dashboardSummaryRepository.loadMetrics(start, end, 5);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("todaySalesCount", metrics.getTodaySalesCount());
        summary.put("totalProducts", metrics.getTotalProducts());
        summary.put("lowStockCount", metrics.getLowStockCount());
        summary.put("totalCustomers", metrics.getTotalCustomers());
        return Collections.unmodifiableMap(summary);
    }

}
