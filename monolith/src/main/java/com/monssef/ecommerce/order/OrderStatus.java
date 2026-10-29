package com.monssef.ecommerce.order;

// An order starts PENDING when placed. PAID comes with the payment service (#45)
public enum OrderStatus {
    PENDING,
    PAID,
    CANCELLED
}
