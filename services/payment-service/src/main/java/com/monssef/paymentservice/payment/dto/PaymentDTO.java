package com.monssef.paymentservice.payment.dto;

import com.monssef.paymentservice.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Tentative de paiement d'une commande")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDTO {

    @Schema(description = "Identifiant", example = "1")
    private Long id;

    @Schema(description = "Commande payée", example = "1")
    private Long orderId;

    @Schema(description = "Montant débité : le total de la commande", example = "1999.98")
    private BigDecimal amount;

    @Schema(description = "SUCCEEDED (commande PAID) ou DECLINED (commande toujours PENDING)", example = "SUCCEEDED")
    private PaymentStatus status;

    @Schema(description = "4 derniers chiffres de la carte", example = "4242")
    private String cardLast4;

    @Schema(description = "Date du paiement", example = "2026-11-03T10:00:00Z")
    private Instant createdAt;
}
