package com.monssef.orderservice.order;

// An order starts PENDING when placed. payment-service sets it to PAID once the payment succeeds.
// It becomes CANCELLED when the user cancels it or when it stays unpaid too long (PendingOrderExpiry);
// its units then go back to the stock. PAID and CANCELLED are final
public enum OrderStatus {
    PENDING,
    PAID,
    CANCELLED
}
