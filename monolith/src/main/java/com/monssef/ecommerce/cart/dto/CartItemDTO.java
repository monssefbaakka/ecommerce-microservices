package com.monssef.ecommerce.cart.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Ligne du panier, au prix actuel du produit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {

    @Schema(description = "Identifiant du produit", example = "1")
    private Long productId;

    @Schema(description = "Nom actuel du produit", example = "iPhone 15")
    private String productName;

    @Schema(description = "Prix actuel du produit", example = "999.99")
    private BigDecimal unitPrice;

    @Schema(description = "Quantité", example = "2")
    private int quantity;

    @Schema(description = "unitPrice x quantity", example = "1999.98")
    private BigDecimal subtotal;
}
