package com.monssef.ecommerce.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Création ou modification d'une catégorie")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    @Schema(description = "Nom unique, insensible à la casse", example = "Electronics")
    private String name;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    @Schema(description = "Description facultative", example = "Phones and laptops")
    private String description;
}
