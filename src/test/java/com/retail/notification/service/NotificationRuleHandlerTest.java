package com.retail.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.notification.entity.NotificationOutbox;
import com.retail.notification.enums.NotificationType;
import com.retail.notification.event.InventoryChangeSource;
import com.retail.notification.event.InventoryStockChangedEvent;
import com.retail.notification.event.SaleCompletedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRuleHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationRuleHandler handler = new NotificationRuleHandler(objectMapper);

    @Test
    void lowStockIsEmittedOnlyWhenCrossingTheThreshold() {
        assertTrue(resolveInventory(10, 7).isEmpty());

        List<NotificationDefinition> notifications = resolveInventory(7, 5);

        assertEquals(1, notifications.size());
        assertEquals(NotificationType.LOW_STOCK, notifications.get(0).type());
        assertTrue(resolveInventory(5, 3).isEmpty(), "remaining below threshold must not duplicate LOW_STOCK");
    }

    @Test
    void nonThresholdManualAdjustmentIsSilent() {
        InventoryStockChangedEvent event = new InventoryStockChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Cotton Shirt", 10, 7,
                InventoryChangeSource.MANUAL_ADJUSTMENT);
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setEventType(event.eventType());
        outbox.setPayload(objectMapper.valueToTree(event));

        assertTrue(handler.resolve(outbox).isEmpty());
    }

    @Test
    void outOfStockTakesPrecedenceOverLowStock() {
        List<NotificationDefinition> notifications = resolveInventory(3, 0);

        assertEquals(1, notifications.size());
        assertEquals(NotificationType.OUT_OF_STOCK, notifications.get(0).type());
    }

    @Test
    void restockFromZeroAboveThresholdEmitsReplenished() {
        List<NotificationDefinition> notifications = resolveInventory(0, 8);

        assertEquals(1, notifications.size());
        assertEquals(NotificationType.STOCK_REPLENISHED, notifications.get(0).type());
    }

    @Test
    void saleAtHighValueThresholdEmitsOwnerOnlyHighValueRule() {
        List<NotificationDefinition> notifications = resolveSale(new BigDecimal("10000"));

        assertEquals(List.of(NotificationType.SALE_COMPLETED, NotificationType.HIGH_VALUE_SALE),
                notifications.stream().map(NotificationDefinition::type).toList());
    }

    @Test
    void saleBelowHighValueThresholdDoesNotEmitHighValueRule() {
        List<NotificationDefinition> notifications = resolveSale(new BigDecimal("9999.99"));

        assertEquals(List.of(NotificationType.SALE_COMPLETED),
                notifications.stream().map(NotificationDefinition::type).toList());
    }

    private List<NotificationDefinition> resolveInventory(int previous, int current) {
        InventoryStockChangedEvent event = new InventoryStockChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Cotton Shirt", previous, current,
                InventoryChangeSource.SALE);
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setEventType(event.eventType());
        outbox.setPayload(objectMapper.valueToTree(event));
        return handler.resolve(outbox);
    }

    private List<NotificationDefinition> resolveSale(BigDecimal total) {
        SaleCompletedEvent event = new SaleCompletedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "S-100", total);
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setEventType(event.eventType());
        outbox.setPayload(objectMapper.valueToTree(event));
        return handler.resolve(outbox);
    }
}
