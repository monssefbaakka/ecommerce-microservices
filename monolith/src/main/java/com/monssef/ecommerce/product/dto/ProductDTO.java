package com.monssef.ecommerce.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Produit du catalogue")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    @Schema(description = "Identifiant", example = "1")
    private Long id;

    @Schema(description = "Nom", example = "iPhone 15")
    private String name;

    @Schema(description = "Description", example = "128 GB")
    private String description;

    @Schema(description = "Prix unitaire", example = "999.99")
    private BigDecimal price;

    @Schema(description = "Identifiant de la catégorie", example = "1")
    private Long categoryId;

    @Schema(description = "Unités disponibles", example = "10")
    private int stock;

    @Schema(description = "Date de création", example = "2026-10-27T10:00:00Z")
    private Instant createdAt;
}
