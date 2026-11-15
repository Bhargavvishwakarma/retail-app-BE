package com.retail.notification.event;

import java.util.Objects;
import java.util.UUID;

/** Event raised for an inventory quantity change in an already-open business transaction. */
public record InventoryStockChangedEvent(
        UUID eventId,
        UUID productId,
        String productName,
        int previousQuantity,
        int currentQuantity,
        InventoryChangeSource source
) implements NotificationBusinessEvent {

    public InventoryStockChangedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(source, "source");
    }

    @Override
    public String eventType() {
        return "INVENTORY_STOCK_CHANGED";
    }

    @Override
    public String aggregateType() {
        return "PRODUCT";
    }

    @Override
    public UUID aggregateId() {
        return productId;
    }
}
