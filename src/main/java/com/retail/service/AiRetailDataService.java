package com.retail.service;

import com.retail.dto.AiDataQueryRequest;
import com.retail.model.UserRole;
import com.retail.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiRetailDataService {
    private static final Map<String, String> CUSTOMER_FIELD_COLUMNS = Map.of(
            "name", "name", "phone", "phone", "email", "email", "address", "address");
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final ActorAssertionService actorAssertions;

    public List<Map<String, Object>> query(AiDataQueryRequest request, String actorToken) {
        if (request.operation() == null) throw new IllegalArgumentException("operation is required");
        ActorAssertionService.Actor actor;
        try { actor = actorAssertions.verify(actorToken); }
        catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid actor assertion");
        }
        var currentUser = users.findById(actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Actor no longer exists"));
        if (currentUser.getRole() != actor.role()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Actor permissions have changed");
        }
        jdbc.setQueryTimeout(5);
        return switch (request.operation()) {
            case PRODUCT_SEARCH -> productSearch(request.query(), limit(request.limit(), 20));
            case CURRENT_INVENTORY -> inventory(request.query(), limit(request.limit(), 30));
            case INVENTORY_SUMMARY -> jdbc.queryForList("select count(*) as activeProducts, " +
                    "coalesce(sum(stock_quantity),0) as totalUnitsInStock from products where active=true");
            case LOW_STOCK -> {
                requireOwner(actor.role());
                int threshold = request.threshold() == null ? 5 : request.threshold();
                if (threshold < 0 || threshold > 10000) throw new IllegalArgumentException("threshold must be between 0 and 10000");
                yield jdbc.queryForList("select p.name, p.sku, p.stock_quantity as stockQuantity, p.price, c.name as category " +
                                "from products p left join categories c on c.id=p.category_id " +
                                "where p.active=true and coalesce(p.stock_quantity,0) < ? order by p.stock_quantity asc nulls first limit ?",
                        threshold, limit(request.limit(), 30));
            }
            case SALES_SUMMARY -> {
                requireOwner(actor.role());
                var range = dateRange(request.from(), request.to());
                yield jdbc.queryForList("select count(*) as salesCount, coalesce(sum(total_amount),0) as revenue " +
                                "from sales where sale_date >= ? and sale_date < ?", range[0], range[1]);
            }
            case BEST_SELLERS -> {
                requireOwner(actor.role());
                var range = dateRange(request.from(), request.to());
                yield jdbc.queryForList("select p.name as product, sum(si.quantity) as quantitySold, " +
                                "sum(si.subtotal) as revenue from sale_items si join sales s on s.id=si.sale_id " +
                                "join products p on p.id=si.product_id where s.sale_date >= ? and s.sale_date < ? " +
                                "group by p.id,p.name order by quantitySold desc limit ?",
                        range[0], range[1], limit(request.limit(), 10));
            }
            case RECENT_SALES -> {
                requireOwner(actor.role());
                yield jdbc.queryForList("select sale_number as saleNumber, sale_date as saleDate, " +
                                "total_amount as totalAmount, payment_method as paymentMethod " +
                                "from sales order by sale_date desc limit ?", limit(request.limit(), 10));
            }
            case CUSTOMER_VISITS -> {
                requireOwner(actor.role());
                var range = dateRange(request.from(), request.to());
                yield jdbc.queryForList("select c.name as customerName, count(*) as transactionCount, " +
                                "(s.customer_id is null) as walkIn " +
                                "from sales s left join customers c on c.id=s.customer_id " +
                                "where s.sale_date >= ? and s.sale_date < ? " +
                                "group by s.customer_id,c.name order by walkIn,c.name limit ?",
                        range[0], range[1], limit(request.limit(), 50));
            }
            case CATEGORY_SUMMARY -> jdbc.queryForList("select c.name as category, count(p.id) as activeProducts, " +
                            "coalesce(sum(p.stock_quantity),0) as unitsInStock from categories c " +
                            "left join products p on p.category_id=c.id and p.active=true " +
                            "group by c.id,c.name order by c.name limit ?", limit(request.limit(), 30));
            case CUSTOMER_SUMMARY -> jdbc.queryForList("select count(*) as totalCustomers, " +
                            "count(*) filter (where created_at >= current_date - interval '30 days') as newCustomersLast30Days " +
                            "from customers");
            case CUSTOMER_DIRECTORY -> customerDirectory(request.fields());
        };
    }

    private List<Map<String, Object>> customerDirectory(List<String> requestedFields) {
        var selectedFields = new LinkedHashSet<String>();
        selectedFields.add("name");
        if (requestedFields != null) requestedFields.stream().filter(java.util.Objects::nonNull)
                .map(field -> field.trim().toLowerCase(Locale.ROOT))
                .filter(CUSTOMER_FIELD_COLUMNS::containsKey).forEach(selectedFields::add);
        Long totalCustomers = jdbc.queryForObject("select count(*) from customers", Long.class);
        String columns = selectedFields.stream().map(CUSTOMER_FIELD_COLUMNS::get)
                .collect(java.util.stream.Collectors.joining(", "));
        List<Map<String, Object>> customers = jdbc.queryForList(
                "select " + columns + " from customers order by name");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCustomers", totalCustomers);
        result.put("customers", customers);
        return List.of(result);
    }

    private List<Map<String, Object>> productSearch(String query, int limit) {
        if (query == null || query.isBlank()) return inventory(null, limit);
        String pattern = "%" + query.trim().replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.queryForList("select p.name, p.sku, p.description, p.size, p.color, p.price, " +
                        "coalesce(p.stock_quantity,0) as stockQuantity, c.name as category " +
                        "from products p left join categories c on c.id=p.category_id " +
                        "where p.active=true and (p.name ilike ? or p.sku ilike ?) order by p.name limit ?",
                pattern, pattern, limit);
    }

    private List<Map<String, Object>> inventory(String query, int limit) {
        if (query != null && !query.isBlank()) return productSearch(query, limit);
        return jdbc.queryForList("select p.name, p.sku, coalesce(p.stock_quantity,0) as stockQuantity, " +
                        "p.price, c.name as category from products p left join categories c on c.id=p.category_id " +
                        "where p.active=true order by p.name limit ?", limit);
    }

    private LocalDate[] dateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from) || from.isBefore(to.minusDays(366))) {
            throw new IllegalArgumentException("from and to are required and must span at most 366 days");
        }
        return new LocalDate[]{from, to};
    }

    private int limit(Integer value, int defaultValue) {
        int limit = value == null ? defaultValue : value;
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        return Math.min(limit, 50);
    }

    private void requireOwner(UserRole role) {
        if (role != UserRole.OWNER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This data is owner-only");
    }
}
