package com.monssef.paymentservice.config;

import com.monssef.paymentservice.support.IntegrationTest;
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
                .andExpect(jsonPath("$.info.title").value("Payment service API"));
    }

    @Test
    void apiDocs_shouldListOnlyThePaymentEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths.length()").value(2))
                .andExpect(jsonPath("$.paths['/api/payments'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/payments'].get.parameters[*].name", hasItems("page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/api/payments/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.amount").doesNotExist());
    }

    @Test
    void everyEndpoint_shouldAskForTheBearerToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/payments'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/payments/{id}'].get.security[0].bearerAuth").exists());
    }

    @Test
    void errorResponses_shouldUseTheErrorResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/payments'].post.responses['409'].content['application/json'].schema['$ref']")
                        .value(ERROR_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/payments/{id}'].get.responses['404'].content['application/json'].schema['$ref']")
                        .value(ERROR_SCHEMA))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.errors").exists());
    }

    @Test
    void swaggerUi_shouldBePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
