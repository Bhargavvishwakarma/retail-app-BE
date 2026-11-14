package com.retail.dto;

import com.retail.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecentSaleResponse(UUID id, String saleNumber, LocalDateTime saleDate,
                                 PaymentMethod paymentMethod, BigDecimal totalAmount) {
}
