package com.monssef.ecommerce.product;

import com.monssef.ecommerce.product.dto.ProductDTO;
import com.monssef.ecommerce.product.dto.ProductRequest;
import com.monssef.ecommerce.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.net.URI;


@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Produits", description = "Gestion des produits du catalogue")
public class ProductController {

    private final ProductService productService;
@GetMapping
@Operation(summary = "Liste des produits", description = "Liste paginée avec recherche par nom et filtre par catégorie")
@ApiResponse(responseCode = "200", description = "Liste paginée récupérée",
        content = @Content(schema = @Schema(implementation = ProductDTO.class)))
public Page<ProductDTO> findAll(
        @RequestParam(required = false) @Parameter(description = "Recherche partielle sur le nom (insensible à la casse)") String name,
        @RequestParam(required = false) @Parameter(description = "Filtre par identifiant de catégorie") Long categoryId,
        @PageableDefault(size = 10, sort = "id") Pageable pageable) {
    return productService.findAll(name, categoryId, pageable);
}

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un produit", description = "Retourne un produit par son identifiant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produit trouvé",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "404", description = "Produit non trouvé", content = @Content)
    })
    public ProductDTO findById(@PathVariable @Parameter(description = "Identifiant du produit") Long id) {
        return productService.findById(id);
    }
    // @Valid triggers the validation annotations of ProductRequest (@NotBlank, @DecimalMin, @NotNull...)
    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Créer un produit", description = "Crée un nouveau produit associé à une catégorie existante")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produit créé",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "404", description = "Catégorie introuvable", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé au rôle ADMIN", content = @Content)
    })
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody ProductRequest request) {
        ProductDTO created = productService.create(request);
        // 201 Created + Location header: /api/products/{id}
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Modifier un produit", description = "Met à jour un produit existant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produit modifié",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produit ou catégorie introuvable", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé au rôle ADMIN", content = @Content)
    })
    public ProductDTO update(@PathVariable @Parameter(description = "Identifiant du produit") Long id,
                             @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204, no body
    @Operation(summary = "Supprimer un produit", description = "Supprime un produit par son identifiant")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Produit supprimé", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produit non trouvé", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé au rôle ADMIN", content = @Content)
    })
    public void delete(@PathVariable @Parameter(description = "Identifiant du produit") Long id) {
        productService.delete(id);
    }
}