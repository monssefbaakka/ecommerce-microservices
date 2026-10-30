package com.monssef.ecommerce.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Title and description shown at the top of Swagger UI
@OpenAPIDefinition(info = @Info(
        title = "E-commerce API",
        version = "0.0.1",
        description = "Catalogue (catégories, produits), panier et commandes. "
                + "Les endpoints marqués d'un cadenas demandent un token JWT : "
                + "POST /api/auth/login, puis Authorize.",
        license = @License(name = "MIT",
                url = "https://github.com/monssefbaakka/ecommerce-microservices/blob/main/LICENSE")))
// Adds the "Authorize" button in Swagger UI: paste the token from /api/auth/login (without "Bearer ")
@Configuration
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    // Referenced by @SecurityRequirement on the protected endpoints
    public static final String BEARER_AUTH = "bearerAuth";
}
