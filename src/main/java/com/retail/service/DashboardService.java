package com.retail.service;

import com.retail.model.Sale;
import com.retail.repository.DashboardSummaryRepository;
import com.retail.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SaleRepository saleRepository;
    private final DashboardSummaryRepository dashboardSummaryRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        var metrics = dashboardSummaryRepository.loadMetrics(start, end, 5);
        List<UUID> recentSaleIds = saleRepository.findRecentSaleIds(PageRequest.of(0, 5));
        List<Sale> recentSales = recentSaleIds.isEmpty()
                ? List.of()
                : restoreRecentSalesOrder(recentSaleIds, saleRepository.findAllWithDetailsByIdIn(recentSaleIds));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("todayRevenue", metrics.getTodayRevenue());
        summary.put("todaySalesCount", metrics.getTodaySalesCount());
        summary.put("totalProducts", metrics.getTotalProducts());
        summary.put("lowStockCount", metrics.getLowStockCount());
        summary.put("totalCustomers", metrics.getTotalCustomers());
        summary.put("recentSales", recentSales);
        return summary;
    }

    private List<Sale> restoreRecentSalesOrder(List<UUID> recentSaleIds, List<Sale> loadedSales) {
        Map<UUID, Sale> salesById = new HashMap<>(loadedSales.size());
        for (Sale sale : loadedSales) {
            salesById.put(sale.getId(), sale);
        }

        List<Sale> orderedSales = new ArrayList<>(recentSaleIds.size());
        for (UUID saleId : recentSaleIds) {
            Sale sale = salesById.get(saleId);
            if (sale != null) {
                orderedSales.add(sale);
            }
        }
        return orderedSales;
    }
}
