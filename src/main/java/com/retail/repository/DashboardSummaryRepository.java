package com.retail.repository;

import com.retail.dto.DashboardMetricsProjection;
import com.retail.model.Sale;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
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

    @Query(value = """
            SELECT CAST(s.sale_date AS date) AS "businessDate",
                   COALESCE(SUM(s.total_amount), 0) AS revenue,
                   COUNT(*) AS transactions
            FROM sales s
            WHERE s.sale_date >= :start AND s.sale_date < :end
            GROUP BY CAST(s.sale_date AS date)
            ORDER BY "businessDate"
            """, nativeQuery = true)
    List<DailySalesProjection> loadDailySales(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    @Query(value = """
            WITH product_sales AS (
              SELECT p.id, p.name, p.sku, c.name AS category,
                     SUM(si.quantity) AS units_sold, SUM(si.subtotal) AS revenue
              FROM sales s
              JOIN sale_items si ON si.sale_id = s.id
              JOIN products p ON p.id = si.product_id
              LEFT JOIN categories c ON c.id = p.category_id
              WHERE s.sale_date >= :start AND s.sale_date < :end
              GROUP BY p.id, p.name, p.sku, c.name
            ),
            ranked_products AS (
              SELECT product_sales.*,
                     ROW_NUMBER() OVER (ORDER BY units_sold DESC, revenue DESC, name ASC) AS quantity_rank,
                     ROW_NUMBER() OVER (ORDER BY revenue DESC, units_sold DESC, name ASC) AS revenue_rank
              FROM product_sales
            )
            SELECT 'QUANTITY' AS ranking, quantity_rank AS "rankingPosition", name, sku, category,
                   units_sold AS "unitsSold", revenue
            FROM ranked_products
            WHERE quantity_rank <= 5
            UNION ALL
            SELECT 'REVENUE' AS ranking, revenue_rank AS "rankingPosition", name, sku, category,
                   units_sold AS "unitsSold", revenue
            FROM ranked_products
            WHERE revenue_rank <= 5
            ORDER BY ranking, "rankingPosition"
            """, nativeQuery = true)
    List<RankedTopProductProjection> findTopProducts(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT COALESCE(s.payment_method, 'UNSPECIFIED') AS method,
                   COALESCE(SUM(s.total_amount), 0) AS amount,
                   COUNT(*) AS transactions
            FROM sales s
            WHERE s.sale_date >= :start AND s.sale_date < :end
            GROUP BY s.payment_method
            ORDER BY amount DESC
            """, nativeQuery = true)
    List<PaymentProjection> loadPayments(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    @Query(value = """
            WITH inventory AS (
              SELECT COUNT(*) AS total_products,
                     COUNT(*) FILTER (WHERE p.stock_quantity > 0
                                        AND p.stock_quantity < :lowStockThreshold) AS low_stock_products,
                     COUNT(*) FILTER (WHERE p.stock_quantity = 0) AS out_of_stock_products,
                     CASE
                       WHEN COUNT(*) FILTER (WHERE COALESCE(p.stock_quantity, 0) > 0
                                               AND COALESCE(p.cost, 0) <= 0) = 0
                       THEN COALESCE(SUM(COALESCE(p.stock_quantity, 0) * p.cost), 0)
                       ELSE NULL
                     END AS inventory_cost_value
              FROM products p
              WHERE p.active = TRUE
            ),
            customer_totals AS (
              SELECT COUNT(*) AS total_customers FROM customers
            ),
            attention AS (
              SELECT p.id, p.name, p.sku, COALESCE(p.stock_quantity, 0) AS quantity,
                     :lowStockThreshold AS reorder_threshold
              FROM products p
              WHERE p.active = TRUE AND p.stock_quantity IS NOT NULL
                AND p.stock_quantity < :lowStockThreshold
              ORDER BY COALESCE(p.stock_quantity, 0) ASC, p.name ASC
              LIMIT :limit
            )
            SELECT inventory.total_products AS "totalProducts",
                   inventory.low_stock_products AS "lowStockProducts",
                   inventory.out_of_stock_products AS "outOfStockProducts",
                   inventory.inventory_cost_value AS "inventoryCostValue",
                   customer_totals.total_customers AS "totalCustomers",
                   attention.id AS id, attention.name AS name, attention.sku AS sku,
                   attention.quantity AS quantity, attention.reorder_threshold AS "reorderThreshold"
            FROM inventory
            CROSS JOIN customer_totals
            LEFT JOIN attention ON TRUE
            ORDER BY attention.quantity ASC NULLS LAST, attention.name ASC NULLS LAST
            """, nativeQuery = true)
    List<InventoryAnalyticsProjection> loadInventoryAnalytics(
            @Param("lowStockThreshold") int lowStockThreshold,
            @Param("limit") int limit);

    @Query(value = """
            SELECT p.id AS id, p.name AS name, p.sku AS sku, c.name AS category,
                   COALESCE(p.stock_quantity, 0) AS quantity
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            LEFT JOIN (
              SELECT si.product_id
              FROM sales s
              JOIN sale_items si ON si.sale_id = s.id
              WHERE s.sale_date >= :since
              GROUP BY si.product_id
            ) recent_sales ON recent_sales.product_id = p.id
            WHERE p.active = TRUE AND COALESCE(p.stock_quantity, 0) > 0
              AND p.created_at < :since AND recent_sales.product_id IS NULL
            ORDER BY p.stock_quantity DESC, p.name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<SlowMovingProductProjection> findSlowMovingProducts(@Param("since") LocalDateTime since,
                                                              @Param("limit") int limit);

    @Query(value = """
            SELECT activity.id AS id, activity.type AS type, activity.title AS title,
                   activity.detail AS detail, activity."occurredAt" AS "occurredAt",
                   activity.amount AS amount, activity."paymentMethod" AS "paymentMethod"
            FROM (
              (
              SELECT s.id, CAST('SALE' AS text) AS type, COALESCE(s.sale_number, 'Sale') AS title,
                     COALESCE(c.name, 'Walk-in customer') AS detail, s.sale_date AS "occurredAt",
                     s.total_amount AS amount, COALESCE(s.payment_method, 'UNSPECIFIED') AS "paymentMethod"
              FROM sales s LEFT JOIN customers c ON c.id = s.customer_id
              ORDER BY s.sale_date DESC NULLS LAST LIMIT 6
              )
              UNION ALL
              (
              SELECT c.id, CAST('CUSTOMER' AS text) AS type, c.name AS title,
                     COALESCE(c.phone, c.email, 'New customer') AS detail, c.created_at AS "occurredAt",
                     CAST(NULL AS numeric) AS amount, CAST(NULL AS text) AS "paymentMethod"
              FROM customers c
              ORDER BY c.created_at DESC NULLS LAST LIMIT 6
              )
              UNION ALL
              (
              SELECT p.id, CAST('PRODUCT' AS text) AS type, p.name AS title,
                     COALESCE(p.sku, 'Product added') AS detail, p.created_at AS "occurredAt",
                     CAST(NULL AS numeric) AS amount, CAST(NULL AS text) AS "paymentMethod"
              FROM products p
              ORDER BY p.created_at DESC NULLS LAST LIMIT 6
              )
            ) activity
            ORDER BY activity."occurredAt" DESC NULLS LAST
            LIMIT 12
            """, nativeQuery = true)
    List<ActivityProjection> findRecentActivities();

    interface DailySalesProjection {
        LocalDate getBusinessDate();

        BigDecimal getRevenue();

        long getTransactions();
    }

    interface RankedTopProductProjection {
        String getRanking();

        long getRankingPosition();

        String getName();

        String getSku();

        String getCategory();

        long getUnitsSold();

        BigDecimal getRevenue();
    }

    interface PaymentProjection {
        String getMethod();

        BigDecimal getAmount();

        long getTransactions();
    }

    interface InventoryAnalyticsProjection {
        long getTotalProducts();

        long getLowStockProducts();

        long getOutOfStockProducts();

        BigDecimal getInventoryCostValue();

        long getTotalCustomers();

        UUID getId();

        String getName();

        String getSku();

        Integer getQuantity();

        Integer getReorderThreshold();
    }

    interface SlowMovingProductProjection {
        UUID getId();

        String getName();

        String getSku();

        String getCategory();

        int getQuantity();
    }

    interface ActivityProjection {
        UUID getId();

        String getType();

        String getTitle();

        String getDetail();

        LocalDateTime getOccurredAt();

        BigDecimal getAmount();

        String getPaymentMethod();
    }
}
