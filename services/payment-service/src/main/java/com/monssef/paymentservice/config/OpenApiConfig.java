package com.monssef.paymentservice.config;

import com.monssef.paymentservice.common.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Title and description shown at the top of Swagger UI
@OpenAPIDefinition(info = @Info(
        title = "Payment service API",
        version = "0.0.1",
        description = "Paiement simulé des commandes de l'utilisateur connecté. "
                + "Tous les endpoints demandent un token JWT : "
                + "POST /api/auth/login sur auth-service (ou la gateway), puis Authorize.",
        license = @License(name = "MIT",
                url = "https://github.com/monssefbaakka/ecommerce-microservices/blob/main/LICENSE")))
// Adds the "Authorize" button in Swagger UI: paste the token from auth-service (without "Bearer ")
@Configuration
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    // Referenced by @SecurityRequirement on the protected endpoints
    public static final String BEARER_AUTH = "bearerAuth";

    // Every 4xx / 5xx response declared without a body gets the ErrorResponse schema,
    // instead of repeating it in each @ApiResponse of the controllers
    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            Content errorContent = new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((code, response) -> {
                        boolean isError = code.startsWith("4") || code.startsWith("5");
                        if (isError && (response.getContent() == null || response.getContent().isEmpty())) {
                            response.setContent(errorContent);
                        }
                    })));
        };
    }
}
