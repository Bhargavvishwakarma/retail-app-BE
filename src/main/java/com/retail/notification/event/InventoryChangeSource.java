package com.retail.notification.event;

/** Identifies the business workflow that changed an inventory quantity. */
public enum InventoryChangeSource {
    SALE,
    MANUAL_ADJUSTMENT
}
