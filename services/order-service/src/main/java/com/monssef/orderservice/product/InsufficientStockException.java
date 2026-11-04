package com.monssef.orderservice.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 409 Conflict when the cart asks for more than the stock, same message as product-service and the monolith
public class InsufficientStockException extends ResponseStatusException {

    public InsufficientStockException(ProductInfo product) {
        super(HttpStatus.CONFLICT, "Not enough stock for product %d (%s): %d left"
                .formatted(product.id(), product.name(), product.stock()));
    }
}
