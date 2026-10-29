package com.monssef.ecommerce.order;

import com.monssef.ecommerce.config.OpenApiConfig;
import com.monssef.ecommerce.order.dto.OrderDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;

// No rule in SecurityConfig for /api/orders: it falls under anyRequest().authenticated()
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Commandes", description = "Commandes de l'utilisateur connecté")
public class OrderController {

    private final OrderService orderService;

    // No body: the order is built from the cart of the logged-in user
    @PostMapping
    @Operation(summary = "Passer commande", description = "Crée une commande à partir de mon panier, puis vide le panier")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Commande créée",
                    content = @Content(schema = @Schema(implementation = OrderDTO.class))),
            @ApiResponse(responseCode = "400", description = "Panier vide", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public ResponseEntity<OrderDTO> placeOrder(@Parameter(hidden = true) Principal principal) {
        OrderDTO created = orderService.placeOrder(principal.getName());
        // 201 Created + Location header: /api/orders/{id}
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Voir une commande", description = "Retourne une de mes commandes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commande trouvée",
                    content = @Content(schema = @Schema(implementation = OrderDTO.class))),
            @ApiResponse(responseCode = "404", description = "Commande introuvable ou d'un autre utilisateur", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public OrderDTO findById(@Parameter(hidden = true) Principal principal,
                             @PathVariable @Parameter(description = "Identifiant de la commande") Long id) {
        return orderService.findById(principal.getName(), id);
    }
}
