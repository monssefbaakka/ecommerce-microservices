package com.monssef.productservice.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One product of an order and its number of units
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockLine {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
