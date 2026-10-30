package com.monssef.ecommerce.user.dto;

import com.monssef.ecommerce.user.Role;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Never expose the password, even hashed
@Schema(description = "Compte utilisateur, sans le mot de passe")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    @Schema(description = "Identifiant", example = "1")
    private Long id;

    @Schema(description = "Email", example = "john@example.com")
    private String email;

    @Schema(description = "Rôle", example = "USER")
    private Role role;

    @Schema(description = "Date de création", example = "2026-10-27T10:00:00Z")
    private Instant createdAt;
}
