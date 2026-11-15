package com.retail.notification.enums;

/**
 * The supported notification vocabulary.  A type can be present before its
 * corresponding business workflow is introduced; rule handlers decide which
 * types are emitted in a given release.
 */
public enum NotificationType {
    LOW_STOCK(NotificationCategory.INVENTORY),
    OUT_OF_STOCK(NotificationCategory.INVENTORY),
    STOCK_REPLENISHED(NotificationCategory.INVENTORY),
    STOCK_ADJUSTED(NotificationCategory.INVENTORY),
    SALE_COMPLETED(NotificationCategory.SALES),
    HIGH_VALUE_SALE(NotificationCategory.SALES),
    SALE_CANCELLED(NotificationCategory.SALES),
    SALE_RETURNED(NotificationCategory.SALES),
    PURCHASE_RECEIVED(NotificationCategory.PURCHASE),
    SUPPLIER_PAYMENT_DUE(NotificationCategory.PAYMENT),
    SUPPLIER_PAYMENT_COMPLETED(NotificationCategory.PAYMENT),
    CUSTOMER_BIRTHDAY(NotificationCategory.CUSTOMER),
    CUSTOMER_BALANCE_DUE(NotificationCategory.CUSTOMER),
    EMPLOYEE_ACTIVITY(NotificationCategory.SYSTEM);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }
}
