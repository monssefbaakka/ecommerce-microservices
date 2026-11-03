package com.monssef.paymentservice.payment;

// SUCCEEDED sets the order to PAID; DECLINED leaves it PENDING, so the user can try another card
public enum PaymentStatus {
    SUCCEEDED,
    DECLINED
}
