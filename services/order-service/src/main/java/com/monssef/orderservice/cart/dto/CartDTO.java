package com.monssef.orderservice.cart.dto;

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

    @Schema(description = "Somme des sous-totaux. null si le prix d'une ligne n'est pas connu (productsUpToDate à false)",
            example = "1999.98")
    private BigDecimal total;

    @Schema(description = "false si product-service est indisponible : noms et prix sont les derniers connus, "
            + "et une ligne jamais vue n'a ni nom ni prix", example = "true")
    private boolean productsUpToDate;

    // Names and prices just read from product-service: the usual case
    public CartDTO(List<CartItemDTO> items, int totalQuantity, BigDecimal total) {
        this(items, totalQuantity, total, true);
    }
}
