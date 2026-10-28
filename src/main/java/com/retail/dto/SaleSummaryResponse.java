package com.retail.dto;

import com.retail.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SaleSummaryResponse(
        UUID id,
        String saleNumber,
        LocalDateTime saleDate,
        String customerName,
        PaymentMethod paymentMethod,
        BigDecimal totalAmount
) {}
