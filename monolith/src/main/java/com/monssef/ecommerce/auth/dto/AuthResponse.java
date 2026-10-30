package com.monssef.ecommerce.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Token JWT à envoyer dans l'en-tête Authorization")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    @Schema(description = "Token JWT signé", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIn0.signature")
    private String token;

    @Schema(description = "Toujours Bearer", example = "Bearer")
    private String tokenType;  // always "Bearer": sent back as "Authorization: Bearer <token>"

    @Schema(description = "Durée de validité en secondes", example = "3600")
    private long expiresIn;    // token lifetime in seconds
}
