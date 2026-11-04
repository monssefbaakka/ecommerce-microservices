package com.monssef.productservice.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 409 Conflict, same message as the monolith: order-service passes it on to the user as is
public class InsufficientStockException extends ResponseStatusException {

    public InsufficientStockException(Product product) {
        super(HttpStatus.CONFLICT, "Not enough stock for product %d (%s): %d left"
                .formatted(product.getId(), product.getName(), product.getStock()));
    }
}
