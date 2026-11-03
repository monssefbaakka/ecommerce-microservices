package com.monssef.paymentservice.order;

// Same values as in order-service, which places the orders: only a PENDING order can be paid
public enum OrderStatus {
    PENDING,
    PAID,
    CANCELLED
}
