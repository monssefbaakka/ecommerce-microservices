package com.monssef.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Panier de l'utilisateur connecté")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {

    @Schema(description = "Lignes du panier")
    private List<CartItemDTO> items;

    @Schema(description = "Nombre total d'unités", example = "2")
    private int totalQuantity;

    @Schema(description = "Somme des sous-totaux", example = "1999.98")
    private BigDecimal total;
}
