package com.monssef.paymentservice.order;

import java.math.BigDecimal;

/**
 * An order as order-service gives it on /internal/orders/{id}: only what a payment needs.
 * The owner is checked here, the call carries a SERVICE token, not the user's.
 */
public record OrderSummary(Long id, String userEmail, OrderStatus status, BigDecimal total) {

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public boolean belongsTo(String email) {
        return userEmail.equals(email);
    }
}
