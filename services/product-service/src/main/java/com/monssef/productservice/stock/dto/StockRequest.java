package com.monssef.productservice.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// The lines of an order, reserved (or released) all together
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRequest {

    @NotEmpty
    @Valid
    private List<StockLine> items;
}
