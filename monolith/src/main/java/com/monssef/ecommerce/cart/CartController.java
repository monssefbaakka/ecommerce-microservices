package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.cart.dto.AddCartItemRequest;
import com.monssef.ecommerce.cart.dto.CartDTO;
import com.monssef.ecommerce.cart.dto.UpdateCartItemRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

// No rule in SecurityConfig for /api/cart: it falls under anyRequest().authenticated()
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Panier", description = "Panier de l'utilisateur connecté")
public class CartController {

    private final CartService cartService;

    // principal.getName() is the email from the JWT (set by JwtAuthenticationFilter)
    @GetMapping
    @Operation(summary = "Voir mon panier", description = "Retourne le panier de l'utilisateur connecté (vide s'il n'existe pas encore)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier récupéré",
                    content = @Content(schema = @Schema(implementation = CartDTO.class))),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public CartDTO getCart(@Parameter(hidden = true) Principal principal) {
        return cartService.getCart(principal.getName());
    }

    @PostMapping("/items")
    @Operation(summary = "Ajouter un produit", description = "Ajoute un produit au panier, ou augmente sa quantité s'il y est déjà")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier mis à jour",
                    content = @Content(schema = @Schema(implementation = CartDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide ou quantité trop grande", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produit introuvable", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public CartDTO addItem(@Parameter(hidden = true) Principal principal,
                           @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(principal.getName(), request);
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Changer la quantité", description = "Remplace la quantité d'un produit du panier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier mis à jour",
                    content = @Content(schema = @Schema(implementation = CartDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produit absent du panier", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public CartDTO updateItem(@Parameter(hidden = true) Principal principal,
                              @PathVariable @Parameter(description = "Identifiant du produit") Long productId,
                              @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(principal.getName(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Retirer un produit", description = "Retire un produit du panier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier mis à jour",
                    content = @Content(schema = @Schema(implementation = CartDTO.class))),
            @ApiResponse(responseCode = "404", description = "Produit absent du panier", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public CartDTO removeItem(@Parameter(hidden = true) Principal principal,
                              @PathVariable @Parameter(description = "Identifiant du produit") Long productId) {
        return cartService.removeItem(principal.getName(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204, no body
    @Operation(summary = "Vider mon panier", description = "Retire tous les produits du panier")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Panier vidé", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public void clear(@Parameter(hidden = true) Principal principal) {
        cartService.clear(principal.getName());
    }
}
