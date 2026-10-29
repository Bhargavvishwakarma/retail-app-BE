package com.retail.dto;

import java.math.BigDecimal;

public interface DashboardMetricsProjection {
    BigDecimal getTodayRevenue();

    long getTodaySalesCount();

    long getTotalProducts();

    long getLowStockCount();

    long getTotalCustomers();
}
