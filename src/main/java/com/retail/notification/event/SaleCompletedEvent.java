package com.retail.notification.event;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Event raised only after a sale has passed all validation and been persisted. */
public record SaleCompletedEvent(
        UUID eventId,
        UUID saleId,
        String saleNumber,
        BigDecimal totalAmount
) implements NotificationBusinessEvent {

    public SaleCompletedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(saleNumber, "saleNumber");
        Objects.requireNonNull(totalAmount, "totalAmount");
    }

    @Override
    public String eventType() {
        return "SALE_COMPLETED";
    }

    @Override
    public String aggregateType() {
        return "SALE";
    }

    @Override
    public UUID aggregateId() {
        return saleId;
    }
}
