package com.monssef.ecommerce.category;

import com.monssef.ecommerce.category.dto.CategoryDTO;
import com.monssef.ecommerce.category.dto.CategoryRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Catégories", description = "Gestion des catégories de produits")
public class CategoryController {

    private final CategoryService service;

    @GetMapping
    @Operation(summary = "Liste des catégories", description = "Retourne toutes les catégories disponibles")
    // No content here: springdoc reads List<CategoryDTO> and documents an array
    @ApiResponse(responseCode = "200", description = "Liste récupérée avec succès")
    public List<CategoryDTO> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une catégorie", description = "Retourne une catégorie par son identifiant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catégorie trouvée",
                    content = @Content(schema = @Schema(implementation = CategoryDTO.class))),
            @ApiResponse(responseCode = "404", description = "Catégorie non trouvée", content = @Content)
    })
    public CategoryDTO findById(@PathVariable @Parameter(description = "Identifiant de la catégorie") Long id) {
        return service.findById(id);
    }

    // @Valid triggers the validation annotations of CategoryRequest (@NotBlank, @Size...)
    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Créer une catégorie", description = "Crée une nouvelle catégorie")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Catégorie créée",
                    content = @Content(schema = @Schema(implementation = CategoryDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "409", description = "Catégorie déjà existante", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé au rôle ADMIN", content = @Content)
    })
    public ResponseEntity<CategoryDTO> create(@Valid @RequestBody CategoryRequest request) {
        CategoryDTO created = service.create(request);
        // 201 Created + Location header: /api/categories/{id}
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Modifier une catégorie", description = "Met à jour une catégorie existante")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catégorie modifiée",
                    content = @Content(schema = @Schema(implementation = CategoryDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "404", description = "Catégorie non trouvée", content = @Content),
            @ApiResponse(responseCode = "409", description = "Catégorie déjà existante", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé au rôle ADMIN", content = @Content)
    })
    public CategoryDTO update(@PathVariable @Parameter(description = "Identifiant de la catégorie") Long id,
                              @Valid @RequestBody CategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204, no body
    @Operation(summary = "Supprimer une catégorie", description = "Supprime une catégorie par son identifiant")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Catégorie supprimée", content = @Content),
            @ApiResponse(responseCode = "404", description = "Catégorie non trouvée", content = @Content),
            @ApiResponse(responseCode = "409", description = "Catégorie encore utilisée par des produits", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé au rôle ADMIN", content = @Content)
    })
    public void delete(@PathVariable @Parameter(description = "Identifiant de la catégorie") Long id) {
        service.delete(id);
    }
}