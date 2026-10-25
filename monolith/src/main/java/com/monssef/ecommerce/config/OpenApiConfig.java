package com.monssef.ecommerce.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Adds the "Authorize" button in Swagger UI: paste the token from /api/auth/login (without "Bearer ")
@Configuration
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    // Referenced by @SecurityRequirement on the protected endpoints
    public static final String BEARER_AUTH = "bearerAuth";
}
