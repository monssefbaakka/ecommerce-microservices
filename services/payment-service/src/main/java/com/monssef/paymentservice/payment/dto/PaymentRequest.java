package com.monssef.paymentservice.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// No amount here: the service charges the total stored with the order, never a price sent by the client
@Schema(description = "Paiement d'une commande par carte")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "L'identifiant de la commande est obligatoire")
    @Schema(description = "Identifiant de la commande à payer", example = "1")
    private Long orderId;

    @NotBlank(message = "Le numéro de carte est obligatoire")
    @Pattern(regexp = "\\d{16}", message = "Le numéro de carte doit contenir 16 chiffres")
    @Schema(description = "Numéro de carte (simulé : se terminant par 0002 = refusé)", example = "4242424242424242")
    private String cardNumber;
}
