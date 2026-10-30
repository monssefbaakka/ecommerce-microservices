package com.monssef.ecommerce.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Catégorie de produits")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    @Schema(description = "Identifiant", example = "1")
    private Long id;

    @Schema(description = "Nom", example = "Electronics")
    private String name;

    @Schema(description = "Description", example = "Phones and laptops")
    private String description;
}
