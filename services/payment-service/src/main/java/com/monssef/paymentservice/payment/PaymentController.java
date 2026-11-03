package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.config.OpenApiConfig;
import com.monssef.paymentservice.payment.dto.PaymentDTO;
import com.monssef.paymentservice.payment.dto.PaymentRequest;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;

// No rule in SecurityConfig for /api/payments: it falls under anyRequest().authenticated()
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Paiements", description = "Paiement des commandes de l'utilisateur connecté")
public class PaymentController {

    private final PaymentService paymentService;

    // A declined card is not an error of the request: the attempt is created (201) with status DECLINED
    @PostMapping
    @Operation(summary = "Payer une commande",
            description = "Débite le total d'une de mes commandes PENDING. Accepté : la commande passe PAID. "
                    + "Refusé (carte se terminant par 0002) : la commande reste PENDING, un autre essai est possible")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Paiement enregistré, SUCCEEDED ou DECLINED",
                    content = @Content(schema = @Schema(implementation = PaymentDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "404", description = "Commande introuvable ou d'un autre utilisateur", content = @Content),
            @ApiResponse(responseCode = "409", description = "Commande déjà payée ou annulée", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public ResponseEntity<PaymentDTO> pay(@Parameter(hidden = true) Principal principal,
                                         @Valid @RequestBody PaymentRequest request) {
        PaymentDTO created = paymentService.pay(principal.getName(), request);
        // 201 Created + Location header: /api/payments/{id}
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Newest first by default; id breaks the tie between payments made at the same instant
    @GetMapping
    @Operation(summary = "Historique de mes paiements", description = "Liste paginée de mes paiements, refusés compris, les plus récents d'abord")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste paginée récupérée"),
            @ApiResponse(responseCode = "400", description = "Tri inconnu (?sort=foo)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public Page<PaymentDTO> findAll(@Parameter(hidden = true) Principal principal,
                                    @ParameterObject
                                    @PageableDefault(size = 10, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
                                    Pageable pageable) {
        return paymentService.findAll(principal.getName(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Voir un paiement", description = "Retourne un de mes paiements")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement trouvé",
                    content = @Content(schema = @Schema(implementation = PaymentDTO.class))),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable ou d'un autre utilisateur", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token absent ou invalide", content = @Content)
    })
    public PaymentDTO findById(@Parameter(hidden = true) Principal principal,
                               @PathVariable @Parameter(description = "Identifiant du paiement") Long id) {
        return paymentService.findById(principal.getName(), id);
    }
}
