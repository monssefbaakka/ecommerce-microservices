package com.monssef.productservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test: the handler only needs a request, a response and an ObjectMapper
class JsonSecurityErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSecurityErrorHandler handler = new JsonSecurityErrorHandler(objectMapper);

    @Test
    void commence_shouldWrite401Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request("/api/products"), response, new BadCredentialsException("bad token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asString()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asString()).isEqualTo("Authentication required");
        assertThat(body.get("path").asString()).isEqualTo("/api/products");
        assertThat(body.get("errors")).isEmpty();
    }

    @Test
    void handle_shouldWrite403Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request("/api/products"), response, new AccessDeniedException("USER role"));

        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("error").asString()).isEqualTo("Forbidden");
        assertThat(body.get("message").asString()).isEqualTo("Access denied");
        assertThat(body.get("path").asString()).isEqualTo("/api/products");
    }

    @Test
    void messages_shouldNotLeakExceptionDetails() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request("/api/products"), response, new BadCredentialsException("JWT signature does not match"));

        assertThat(response.getContentAsString()).doesNotContain("signature");
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }
}
