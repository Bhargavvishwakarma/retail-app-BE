package com.retail.service;

import com.retail.repository.CustomerRepository;
import com.retail.repository.ProductRepository;
import com.retail.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public Map<String, Object> getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("todayRevenue", saleRepository.sumTotalBetween(start, end));
        summary.put("todaySalesCount", saleRepository.countBySaleDateBetween(start, end));
        summary.put("totalProducts", productRepository.countByActiveTrue());
        summary.put("lowStockCount", productRepository.countByActiveTrueAndStockQuantityLessThan(5));
        summary.put("totalCustomers", customerRepository.count());
        summary.put("recentSales", saleRepository.findTop5ByOrderBySaleDateDesc());
        return summary;
    }
}
