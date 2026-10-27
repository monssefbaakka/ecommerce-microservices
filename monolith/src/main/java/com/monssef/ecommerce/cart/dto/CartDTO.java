package com.monssef.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {

    private List<CartItemDTO> items;
    private int totalQuantity;
    private BigDecimal total;
}
