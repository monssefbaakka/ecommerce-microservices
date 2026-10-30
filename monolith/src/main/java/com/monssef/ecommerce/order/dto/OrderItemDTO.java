package com.monssef.ecommerce.order.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Ligne de commande : nom et prix copiés au moment de l'achat")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {

    @Schema(description = "Identifiant du produit, null s'il a été supprimé depuis", example = "1")
    private Long productId; // null if the product was deleted since

    @Schema(description = "Nom au moment de l'achat", example = "iPhone 15")
    private String productName;

    @Schema(description = "Prix au moment de l'achat", example = "999.99")
    private BigDecimal unitPrice;

    @Schema(description = "Quantité", example = "2")
    private int quantity;

    @Schema(description = "unitPrice x quantity", example = "1999.98")
    private BigDecimal subtotal;
}
