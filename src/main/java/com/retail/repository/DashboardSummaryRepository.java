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
            SELECT
              COALESCE((SELECT SUM(s.total_amount) FROM sales s
                        WHERE s.sale_date >= :start AND s.sale_date < :end), 0) AS "todayRevenue",
              (SELECT COUNT(*) FROM sales s
               WHERE s.sale_date >= :start AND s.sale_date < :end) AS "todaySalesCount",
              (SELECT COUNT(*) FROM products p WHERE p.active = TRUE) AS "totalProducts",
              (SELECT COUNT(*) FROM products p
               WHERE p.active = TRUE AND p.stock_quantity < :lowStockThreshold) AS "lowStockCount",
              (SELECT COUNT(*) FROM customers) AS "totalCustomers"
            """, nativeQuery = true)
    DashboardMetricsProjection loadMetrics(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("lowStockThreshold") int lowStockThreshold);
}
