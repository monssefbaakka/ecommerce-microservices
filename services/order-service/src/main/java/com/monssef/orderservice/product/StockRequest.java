package com.monssef.orderservice.product;

import java.util.List;

// Body of a reservation or a release in product-service: the lines of the order, handled all together
public record StockRequest(List<StockLine> items) {
}
