package com.monssef.orderservice.product;

// One line of an order sent to product-service: which product, how many units
public record StockLine(Long productId, int quantity) {
}
