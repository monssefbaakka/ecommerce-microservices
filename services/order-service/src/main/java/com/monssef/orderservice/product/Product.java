package com.monssef.orderservice.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The products of product-service, which creates and edits them. Only what the cart and the orders need
 * is mapped: name and price (copied into the order) and the stock, which an order decreases.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Units left, shared with product-service: an order locks the row before changing it
    @Column(nullable = false)
    private int stock;

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }

    // Called when an order is placed: 409 if fewer units are left than ordered
    public void removeStock(int quantity) {
        if (!hasStock(quantity)) {
            throw new InsufficientStockException(this);
        }
        stock -= quantity;
    }
}
