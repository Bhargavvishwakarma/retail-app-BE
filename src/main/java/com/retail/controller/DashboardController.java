package com.retail.controller;

import com.retail.dto.DashboardAnalyticsResponse;
import com.retail.service.DashboardAnalyticsService;
import com.retail.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardAnalyticsService dashboardAnalyticsService;

    @GetMapping("/analytics")
    public DashboardAnalyticsResponse analytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardAnalyticsService.getAnalytics(from, to);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/operational")
    public Map<String, Object> operationalSummary() {
        return dashboardService.getOperationalSummary();
    }
}
