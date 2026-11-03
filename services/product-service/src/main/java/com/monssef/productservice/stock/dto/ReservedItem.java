package com.monssef.productservice.stock.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// What order-service copies into the order line: name and price at the moment of the reservation
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservedItem {

    private Long productId;
    private String name;
    private BigDecimal price;
    private int quantity;
}
