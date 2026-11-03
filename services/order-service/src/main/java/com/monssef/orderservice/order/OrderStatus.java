package com.monssef.orderservice.order;

// An order starts PENDING when placed. payment-service sets it to PAID once the payment succeeds
public enum OrderStatus {
    PENDING,
    PAID,
    CANCELLED
}
