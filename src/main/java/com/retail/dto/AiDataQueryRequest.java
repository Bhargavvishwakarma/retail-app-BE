package com.retail.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

public record AiDataQueryRequest(Operation operation, @Size(max = 100) String query,
                                 LocalDate from, LocalDate to, Integer threshold, Integer limit,
                                 @Min(0) @Max(1000000) Integer offset,
                                 @Size(max = 4) List<@Size(max = 20) String> fields) {
    public AiDataQueryRequest(Operation operation, String query, LocalDate from, LocalDate to,
                              Integer threshold, Integer limit, List<String> fields) {
        this(operation, query, from, to, threshold, limit, null, fields);
    }

    public enum Operation {
        PRODUCT_SEARCH, CURRENT_INVENTORY, INVENTORY_SUMMARY, LOW_STOCK, SALES_SUMMARY,
        BEST_SELLERS, RECENT_SALES, CUSTOMER_VISITS, CATEGORY_SUMMARY, CUSTOMER_SUMMARY, CUSTOMER_DIRECTORY
    }
}
