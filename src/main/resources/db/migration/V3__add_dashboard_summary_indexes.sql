CREATE INDEX IF NOT EXISTS idx_sales_sale_date_summary
    ON sales (sale_date DESC)
    INCLUDE (id, total_amount);

CREATE INDEX IF NOT EXISTS idx_products_active_stock_quantity
    ON products (stock_quantity)
    WHERE active = TRUE;
