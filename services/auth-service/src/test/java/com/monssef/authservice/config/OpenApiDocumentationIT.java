package com.monssef.authservice.config;

import com.monssef.authservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The generated OpenAPI definition, read without a token like Swagger UI does
class OpenApiDocumentationIT extends IntegrationTest {

    private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

    @Test
    void apiDocs_shouldBePublicAndDescribeTheService() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Auth service API"));
    }

    @Test
    void apiDocs_shouldListOnlyTheAuthEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths.length()").value(2))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['200']").exists());
    }

    @Test
    void errorResponses_shouldUseTheErrorResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['409'].content['application/json'].schema['$ref']")
                        .value(ERROR_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401'].content['application/json'].schema['$ref']")
                        .value(ERROR_SCHEMA))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.errors").exists());
    }

    @Test
    void swaggerUi_shouldBePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
