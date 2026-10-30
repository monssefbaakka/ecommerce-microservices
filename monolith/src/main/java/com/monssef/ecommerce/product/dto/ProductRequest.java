package com.monssef.ecommerce.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Création ou modification d'un produit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    @Schema(description = "Nom", example = "iPhone 15")
    private String name;

    @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
    @Schema(description = "Description facultative", example = "128 GB")
    private String description;

    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "0.01", message = "Le prix doit être supérieur à 0")
    @Digits(integer = 8, fraction = 2, message = "Le prix est invalide")
    @Schema(description = "Prix unitaire, 2 décimales au plus", example = "999.99")
    private BigDecimal price;

    @NotNull(message = "L'identifiant de la catégorie est obligatoire")
    @Schema(description = "Identifiant d'une catégorie existante", example = "1")
    private Long categoryId;

    @NotNull(message = "Le stock est obligatoire")
    @Min(value = 0, message = "Le stock ne peut pas être négatif")
    @Schema(description = "Unités disponibles", example = "10")
    private Integer stock;
}
