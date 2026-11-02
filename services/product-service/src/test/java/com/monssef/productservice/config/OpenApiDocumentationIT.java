package com.monssef.productservice.config;

import com.monssef.productservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItems;
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
                .andExpect(jsonPath("$.info.title").value("Product service API"));
    }

    @Test
    void apiDocs_shouldListOnlyTheCatalogEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths.length()").value(4))
                .andExpect(jsonPath("$.paths['/api/categories'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/categories/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[*].name",
                        hasItems("name", "categoryId", "page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put.responses['200']").exists());
    }

    @Test
    void writes_shouldAskForTheBearerToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/products'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist());
    }

    @Test
    void errorResponses_shouldUseTheErrorResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/products'].post.responses['403'].content['application/json'].schema['$ref']")
                        .value(ERROR_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/categories/{id}'].delete.responses['409'].content['application/json'].schema['$ref']")
                        .value(ERROR_SCHEMA))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.errors").exists());
    }

    @Test
    void swaggerUi_shouldBePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
