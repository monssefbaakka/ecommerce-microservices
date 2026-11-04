package com.monssef.orderservice.product;

import java.math.BigDecimal;

// A product as product-service sends it (its other fields are ignored): current name, price and stock
public record ProductInfo(Long id, String name, BigDecimal price, int stock) {

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }
}
