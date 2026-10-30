package com.monssef.ecommerce.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Format commun de toutes les erreurs")
public record ErrorResponse(
        @Schema(description = "Code HTTP", example = "400")
        int status,
        @Schema(description = "Nature de l'erreur", example = "Validation failed")
        String error,
        @Schema(description = "Message lisible", example = "Un ou plusieurs champs sont invalides")
        String message,
        @Schema(description = "URL appelée", example = "/api/products")
        String path,
        @Schema(description = "Champs invalides d'un @Valid, vide pour les autres erreurs")
        List<FieldError> errors
) {

    @Schema(description = "Un champ invalide")
    public record FieldError(
            @Schema(example = "price") String field,
            @Schema(example = "Le prix doit être supérieur à 0") String message) {}
}
