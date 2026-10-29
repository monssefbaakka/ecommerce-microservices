package com.monssef.ecommerce.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 409 Conflict, answered by the ResponseStatusException handler of GlobalExceptionHandler
public class InsufficientStockException extends ResponseStatusException {

    public InsufficientStockException(Product product) {
        super(HttpStatus.CONFLICT, "Not enough stock for product %d (%s): %d left"
                .formatted(product.getId(), product.getName(), product.getStock()));
    }
}
