package com.monssef.ecommerce.cart.dto;

import com.monssef.ecommerce.cart.CartService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequest {

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être au moins 1")
    @Max(value = CartService.MAX_QUANTITY, message = "La quantité ne doit pas dépasser " + CartService.MAX_QUANTITY)
    private Integer quantity;
}
