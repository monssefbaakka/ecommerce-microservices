package com.monssef.authservice.config;

import com.monssef.authservice.common.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Title and description shown at the top of Swagger UI
@OpenAPIDefinition(info = @Info(
        title = "Auth service API",
        version = "0.0.1",
        description = "Inscription et connexion. Le token renvoyé par POST /api/auth/login "
                + "est accepté par tous les services qui partagent la clé JWT_SECRET.",
        license = @License(name = "MIT",
                url = "https://github.com/monssefbaakka/ecommerce-microservices/blob/main/LICENSE")))
@Configuration
public class OpenApiConfig {

    // Every 4xx / 5xx response declared without a body gets the ErrorResponse schema,
    // instead of repeating it in each @ApiResponse of the controller
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
