package com.monssef.ecommerce.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Inscription d'un nouvel utilisateur (rôle USER)")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email n'est pas valide")
    @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
    @Schema(description = "Email, enregistré en minuscules", example = "john@example.com")
    private String email;

    // BCrypt ignores everything after 72 bytes
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
    @Schema(description = "Mot de passe, 8 à 72 caractères", example = "secret123")
    private String password;
}
