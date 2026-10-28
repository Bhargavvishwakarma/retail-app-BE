import java.sql.*;

public class SeedRetailData {
  static int count(Connection c, String sql, String tag) throws SQLException {
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, tag);
      try (ResultSet r = s.executeQuery()) { r.next(); return r.getInt(1); }
    }
  }

  static int saleItemCount(Connection c, String tag) throws SQLException {
    try (PreparedStatement s = c.prepareStatement(
        "SELECT count(*) FROM sale_items i JOIN sales sa ON sa.id = i.sale_id WHERE position(? || '-SALE-' in sa.sale_number) = 1")) {
      s.setString(1, tag);
      try (ResultSet r = s.executeQuery()) { r.next(); return r.getInt(1); }
    }
  }

  public static void main(String[] args) {
    String tag = System.getenv("LOAD_TEST_TAG");
    try (Connection c = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement s = c.prepareStatement("SELECT set_config('app.lt_prefix', ?, true)")) {
          s.setString(1, tag);
          s.execute();
        }
        if (count(c, "SELECT count(*) FROM categories WHERE position(? || '_CATEGORY_' in name) = 1", tag) != 0 ||
            count(c, "SELECT count(*) FROM customers WHERE position(? || '_CUSTOMER_' in name) = 1", tag) != 0 ||
            count(c, "SELECT count(*) FROM products WHERE position(? || '_SKU_' in sku) = 1", tag) != 0 ||
            count(c, "SELECT count(*) FROM sales WHERE position(? || '-SALE-' in sale_number) = 1", tag) != 0) {
          throw new SQLException("Run tag collision", "LT001");
        }

        String[] sql = {
          """
          INSERT INTO categories (name, description)
          SELECT current_setting('app.lt_prefix') || '_CATEGORY_' || lpad(g::text, 2, '0'), 'Synthetic load test data'
          FROM generate_series(1, 8) AS s(g)
          """,
          """
          INSERT INTO customers (name, phone, email, address, created_at)
          SELECT current_setting('app.lt_prefix') || '_CUSTOMER_' || lpad(g::text, 5, '0'),
                 current_setting('app.lt_prefix') || '-' || lpad(g::text, 5, '0'),
                 lower(replace(current_setting('app.lt_prefix'), '_', '.')) || '.customer.' || lpad(g::text, 5, '0') || '@example.invalid',
                 'SYNTHETIC LOAD TEST DATA', current_timestamp - random() * interval '90 days'
          FROM generate_series(1, 10000) AS s(g)
          """,
          """
          CREATE TEMP TABLE lt_categories ON COMMIT DROP AS
          SELECT id, row_number() OVER (ORDER BY name)::int AS rn
          FROM categories
          WHERE position(current_setting('app.lt_prefix') || '_CATEGORY_' in name) = 1
          """,
          """
          INSERT INTO products (name, sku, description, size, color, category_id, price, cost, stock_quantity, active, created_at)
          SELECT current_setting('app.lt_prefix') || '_PRODUCT_' || lpad(g::text, 4, '0'),
                 current_setting('app.lt_prefix') || '_SKU_' || lpad(g::text, 4, '0'),
                 'Synthetic load test item',
                 (ARRAY['S','M','L','XL','Free size'])[((g - 1) % 5) + 1],
                 (ARRAY['Maroon','Blue','Green','Gold','Black'])[((g - 1) % 5) + 1],
                 cat.id,
                 round((100 + ((g * 137) % 250000) / 100.0)::numeric, 2),
                 round(((100 + ((g * 137) % 250000) / 100.0) * 0.62)::numeric, 2),
                 250, true, current_timestamp - random() * interval '90 days'
          FROM generate_series(1, 2000) AS s(g)
          JOIN lt_categories cat ON cat.rn = ((g - 1) % 8) + 1
          """,
          """
          CREATE TEMP TABLE lt_customers ON COMMIT DROP AS
          SELECT id, row_number() OVER (ORDER BY name)::int AS rn
          FROM customers
          WHERE position(current_setting('app.lt_prefix') || '_CUSTOMER_' in name) = 1
          """,
          """
          CREATE TEMP TABLE lt_products ON COMMIT DROP AS
          SELECT id, price, row_number() OVER (ORDER BY sku)::int AS rn
          FROM products
          WHERE position(current_setting('app.lt_prefix') || '_SKU_' in sku) = 1
          """,
          """
          CREATE TEMP TABLE lt_sale_plan ON COMMIT DROP AS
          SELECT g AS sale_seq, c.id AS customer_id, p1.id AS product1_id, p1.price AS price1,
                 p2.id AS product2_id, p2.price AS price2,
                 ((g * 7) % 4 + 1)::int AS quantity1,
                 ((g * 11) % 4 + 1)::int AS quantity2,
                 current_timestamp - random() * interval '30 days' AS sale_date,
                 CASE g % 3 WHEN 0 THEN 'CASH' WHEN 1 THEN 'CARD' ELSE 'UPI' END AS payment_method
          FROM generate_series(1, 10000) AS s(g)
          JOIN lt_customers c ON c.rn = ((g - 1) % 10000) + 1
          JOIN lt_products p1 ON p1.rn = ((g * 17 - 1) % 2000) + 1
          JOIN lt_products p2 ON p2.rn = ((g * 31 + 7) % 2000) + 1
          """,
          """
          INSERT INTO sales (sale_number, customer_id, sale_date, payment_method, total_amount)
          SELECT current_setting('app.lt_prefix') || '-SALE-' || lpad(p.sale_seq::text, 5, '0'),
                 p.customer_id, p.sale_date, p.payment_method,
                 round(p.price1 * p.quantity1 + p.price2 * p.quantity2, 2)
          FROM lt_sale_plan p
          """,
          """
          INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal)
          SELECT s.id, item.product_id, item.quantity, item.unit_price,
                 round(item.unit_price * item.quantity, 2)
          FROM lt_sale_plan p
          JOIN sales s ON s.sale_number = current_setting('app.lt_prefix') || '-SALE-' || lpad(p.sale_seq::text, 5, '0')
          CROSS JOIN LATERAL (VALUES (p.product1_id, p.price1, p.quantity1),
                                     (p.product2_id, p.price2, p.quantity2)) AS item(product_id, unit_price, quantity)
          """
        };
        try (Statement statement = c.createStatement()) {
          for (String query : sql) statement.executeUpdate(query);
        }

        int categories = count(c, "SELECT count(*) FROM categories WHERE position(? || '_CATEGORY_' in name) = 1", tag);
        int customers = count(c, "SELECT count(*) FROM customers WHERE position(? || '_CUSTOMER_' in name) = 1", tag);
        int products = count(c, "SELECT count(*) FROM products WHERE position(? || '_SKU_' in sku) = 1", tag);
        int sales = count(c, "SELECT count(*) FROM sales WHERE position(? || '-SALE-' in sale_number) = 1", tag);
        int items = saleItemCount(c, tag);
        if (categories != 8 || customers != 10000 || products != 2000 || sales != 10000 || items != 20000) {
          throw new SQLException("Verification count mismatch", "LT002");
        }
        c.commit();
        System.out.printf("Committed tag=%s categories=%d customers=%d products=%d sales=%d sale_items=%d%n", tag, categories, customers, products, sales, items);
      } catch (Exception e) {
        c.rollback();
        String state = e instanceof SQLException ? ((SQLException)e).getSQLState() : "n/a";
        System.err.println("Load rolled back; no partial rows retained. Failure type=" + e.getClass().getSimpleName() + " SQLSTATE=" + state);
        System.exit(2);
      }
    } catch (Exception e) {
      String state = e instanceof SQLException ? ((SQLException)e).getSQLState() : "n/a";
      System.err.println("Database load did not complete. Failure type=" + e.getClass().getSimpleName() + " SQLSTATE=" + state);
      System.exit(1);
    }
  }
}
