package com.monssef.ecommerce.auth;

import com.monssef.ecommerce.auth.dto.AuthResponse;
import com.monssef.ecommerce.auth.dto.LoginRequest;
import com.monssef.ecommerce.user.UserService;
import com.monssef.ecommerce.user.dto.RegisterRequest;
import com.monssef.ecommerce.user.dto.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription et connexion des utilisateurs")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    // @Valid triggers the validation annotations of RegisterRequest (@Email, @Size...)
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED) // 201, no Location header: there is no GET /api/users/{id} yet
    @Operation(summary = "Créer un compte", description = "Inscrit un nouvel utilisateur avec le rôle USER")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compte créé",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email déjà utilisé", content = @Content)
    })
    public UserDTO register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter", description = "Vérifie les identifiants et renvoie un token JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion réussie",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "401", description = "Email ou mot de passe incorrect", content = @Content)
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
