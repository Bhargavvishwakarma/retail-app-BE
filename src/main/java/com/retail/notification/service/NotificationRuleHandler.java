package com.retail.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.retail.notification.entity.NotificationOutbox;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.enums.NotificationType;
import com.retail.notification.event.InventoryStockChangedEvent;
import com.retail.notification.event.SaleCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Converts committed business-event payloads into Release 1 notification rules. */
@Service
@RequiredArgsConstructor
public class NotificationRuleHandler {

    public static final int LOW_STOCK_THRESHOLD = 5;
    public static final BigDecimal HIGH_VALUE_SALE_THRESHOLD = new BigDecimal("10000");

    private final ObjectMapper objectMapper;

    public List<NotificationDefinition> resolve(NotificationOutbox outbox) {
        try {
            return switch (outbox.getEventType()) {
                case "SALE_COMPLETED" -> saleCompleted(objectMapper.treeToValue(outbox.getPayload(), SaleCompletedEvent.class));
                case "INVENTORY_STOCK_CHANGED" -> inventoryChanged(
                        objectMapper.treeToValue(outbox.getPayload(), InventoryStockChangedEvent.class));
                default -> throw new IllegalStateException("Unsupported notification event type");
            };
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read notification event payload", exception);
        }
    }

    private List<NotificationDefinition> saleCompleted(SaleCompletedEvent event) {
        List<NotificationDefinition> notifications = new ArrayList<>();
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("saleNumber", event.saleNumber())
                .put("totalAmount", event.totalAmount());
        String total = displayAmount(event.totalAmount());

        notifications.add(new NotificationDefinition(
                NotificationType.SALE_COMPLETED, NotificationSeverity.SUCCESS,
                "Sale completed", "Sale " + event.saleNumber() + " was completed for Rs. " + total + ".",
                "SALE", event.saleId(), metadata));

        if (event.totalAmount().compareTo(HIGH_VALUE_SALE_THRESHOLD) >= 0) {
            notifications.add(new NotificationDefinition(
                    NotificationType.HIGH_VALUE_SALE, NotificationSeverity.HIGH,
                    "High-value sale", "Sale " + event.saleNumber() + " reached Rs. " + total + ".",
                    "SALE", event.saleId(), metadata.deepCopy()));
        }
        return notifications;
    }

    private List<NotificationDefinition> inventoryChanged(InventoryStockChangedEvent event) {
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("productName", event.productName())
                .put("previousQuantity", event.previousQuantity())
                .put("currentQuantity", event.currentQuantity())
                .put("source", event.source().name());

        // Out-of-stock must win whenever a downward transition reaches zero.
        if (event.previousQuantity() > 0 && event.currentQuantity() <= 0) {
            return List.of(new NotificationDefinition(
                    NotificationType.OUT_OF_STOCK, NotificationSeverity.HIGH,
                    "Product out of stock", event.productName() + " is now out of stock.",
                    "PRODUCT", event.productId(), metadata));
        }
        if (event.previousQuantity() > LOW_STOCK_THRESHOLD && event.currentQuantity() > 0
                && event.currentQuantity() <= LOW_STOCK_THRESHOLD) {
            return List.of(new NotificationDefinition(
                    NotificationType.LOW_STOCK, NotificationSeverity.WARNING,
                    "Low stock", event.productName() + " has " + event.currentQuantity() + " units remaining.",
                    "PRODUCT", event.productId(), metadata));
        }
        if (event.previousQuantity() <= 0 && event.currentQuantity() > 0) {
            return List.of(new NotificationDefinition(
                    NotificationType.STOCK_REPLENISHED, NotificationSeverity.SUCCESS,
                    "Stock replenished", event.productName() + " was replenished to "
                            + event.currentQuantity() + " units.",
                    "PRODUCT", event.productId(), metadata));
        }

        // Product update is not a dedicated inventory-adjustment workflow. A
        // non-threshold change (for example 10 -> 7) is intentionally silent,
        // preventing noise and repeated alerts while stock remains low.
        return List.of();
    }

    private String displayAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
