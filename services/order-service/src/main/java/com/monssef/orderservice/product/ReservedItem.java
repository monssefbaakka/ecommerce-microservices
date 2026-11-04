package com.monssef.orderservice.product;

import java.math.BigDecimal;

// Answer of product-service to a reservation: the name and price to copy into the order line
public record ReservedItem(Long productId, String name, BigDecimal price, int quantity) {
}
