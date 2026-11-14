package com.retail.repository;

import com.retail.dto.DashboardMetricsProjection;
import com.retail.model.Sale;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DashboardSummaryRepository extends Repository<Sale, UUID> {

    @Query(value = """
            WITH sales_metrics AS (
              SELECT COALESCE(SUM(s.total_amount), 0) AS today_revenue,
                     COUNT(*) AS today_sales_count
              FROM sales s
              WHERE s.sale_date >= :start AND s.sale_date < :end
            ),
            product_metrics AS (
              SELECT COUNT(*) AS total_products,
                     COUNT(*) FILTER (WHERE p.stock_quantity < :lowStockThreshold) AS low_stock_count
              FROM products p
              WHERE p.active = TRUE
            ),
            customer_metrics AS (
              SELECT COUNT(*) AS total_customers FROM customers
            )
            SELECT sales_metrics.today_revenue AS "todayRevenue",
                   sales_metrics.today_sales_count AS "todaySalesCount",
                   product_metrics.total_products AS "totalProducts",
                   product_metrics.low_stock_count AS "lowStockCount",
                   customer_metrics.total_customers AS "totalCustomers"
            FROM sales_metrics
            CROSS JOIN product_metrics
            CROSS JOIN customer_metrics
            """, nativeQuery = true)
    DashboardMetricsProjection loadMetrics(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("lowStockThreshold") int lowStockThreshold);
}
