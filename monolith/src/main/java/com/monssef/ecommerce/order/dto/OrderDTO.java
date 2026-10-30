package com.monssef.ecommerce.order.dto;

import com.monssef.ecommerce.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Commande de l'utilisateur connecté")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    @Schema(description = "Identifiant", example = "1")
    private Long id;

    @Schema(description = "Statut", example = "PENDING")
    private OrderStatus status;

    @Schema(description = "Lignes de la commande")
    private List<OrderItemDTO> items;

    @Schema(description = "Nombre total d'unités", example = "2")
    private int totalQuantity;

    @Schema(description = "Total payé, figé à la commande", example = "1999.98")
    private BigDecimal total;

    @Schema(description = "Date de la commande", example = "2026-10-27T10:00:00Z")
    private Instant createdAt;
}
