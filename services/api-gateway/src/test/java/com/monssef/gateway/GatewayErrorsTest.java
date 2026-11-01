package com.monssef.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * The gateway with nothing behind it: no auth-service instance known,
 * and a monolith URL on a free port where nothing listens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class GatewayErrorsTest {

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void monolithDown(DynamicPropertyRegistry registry) throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        } // closed: nothing listens on it any more
        registry.add("MONOLITH_URL", () -> "http://localhost:" + freePort);
    }

    @Test
    void serviceNotRegistered_shouldReturn503InTheCommonFormat() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").isEqualTo("Unable to find instance for auth-service")
                .jsonPath("$.path").isEqualTo("/api/auth/login")
                .jsonPath("$.errors").isEmpty();
    }

    @Test
    void serviceNotAnswering_shouldReturn503WithoutItsAddress() {
        client.get().uri("/api/products")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Service unavailable")
                .jsonPath("$.path").isEqualTo("/api/products");
    }

    @Test
    void unknownPath_shouldReturn404InTheCommonFormat() {
        client.get().uri("/unknown")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.message").isEqualTo("No route for this path")
                .jsonPath("$.path").isEqualTo("/unknown");
    }
}
