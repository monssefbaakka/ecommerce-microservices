package com.monssef.ecommerce.cart.dto;

import com.monssef.ecommerce.cart.CartService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Produit à ajouter au panier")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddCartItemRequest {

    @NotNull(message = "L'identifiant du produit est obligatoire")
    @Schema(description = "Identifiant du produit", example = "1")
    private Long productId;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être au moins 1")
    @Max(value = CartService.MAX_QUANTITY, message = "La quantité ne doit pas dépasser " + CartService.MAX_QUANTITY)
    @Schema(description = "Quantité à ajouter, cumulée si le produit est déjà dans le panier", example = "2")
    private Integer quantity;
}
