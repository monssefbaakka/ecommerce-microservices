package com.monssef.orderservice.order;

// Why an order was cancelled: the "reason" tag of the orders.cancelled counter
public enum CancelReason {
    CUSTOMER,   // POST /api/orders/{id}/cancel
    EXPIRED     // still PENDING after orders.pending-expiry.timeout
}
