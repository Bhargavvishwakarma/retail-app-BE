CREATE INDEX IF NOT EXISTS idx_sale_items_sale_product_dashboard
    ON sale_items (sale_id, product_id)
    INCLUDE (quantity, subtotal);

CREATE INDEX IF NOT EXISTS idx_customers_created_at_dashboard
    ON customers (created_at DESC)
    INCLUDE (id, name, phone, email);

CREATE INDEX IF NOT EXISTS idx_products_created_at_dashboard
    ON products (created_at DESC)
    INCLUDE (id, name, sku);
