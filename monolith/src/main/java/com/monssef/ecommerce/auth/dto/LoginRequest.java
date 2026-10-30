package com.monssef.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// No @Email or @Size here: a bad format simply ends in 401, the rules belong to register
@Schema(description = "Identifiants de connexion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "L'email est obligatoire")
    @Schema(description = "Email du compte", example = "john@example.com")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Schema(description = "Mot de passe", example = "secret123")
    private String password;
}
