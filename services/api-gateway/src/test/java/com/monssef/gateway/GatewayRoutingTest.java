package com.monssef.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The real gateway on a random port, in front of two fake services.
 * No Eureka server: auth-service is declared in the simple discovery client, which lb:// also reads.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class GatewayRoutingTest {

    private static final StubService AUTH_SERVICE = new StubService("auth");
    private static final StubService MONOLITH = new StubService("monolith");

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void downstreamServices(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", AUTH_SERVICE::url);
        registry.add("MONOLITH_URL", MONOLITH::url);
    }

    @AfterAll
    static void stopStubs() {
        AUTH_SERVICE.close();
        MONOLITH.close();
    }

    @Test
    void login_shouldGoToAuthServiceWithPathAndBodyUnchanged() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"john@example.com\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("auth")
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.path").isEqualTo("/api/auth/login")
                .jsonPath("$.body").isEqualTo("{\"email\":\"john@example.com\"}");
    }

    @Test
    void register_shouldGoToAuthService() {
        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("auth")
                .jsonPath("$.path").isEqualTo("/api/auth/register");
    }

    @Test
    void catalog_shouldStillGoToTheMonolith() {
        client.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("monolith")
                .jsonPath("$.path").isEqualTo("/api/products");
    }

    @Test
    void authorizationHeader_shouldReachTheService() {
        client.get().uri("/api/cart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-token")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("monolith")
                .jsonPath("$.authorization").isEqualTo("Bearer some-token");
    }

    @Test
    void serviceStatus_shouldBeReturnedAsIs() {
        // e.g. the monolith's 401 for a cart without token: the gateway does not check tokens itself
        client.get().uri("/api/cart?status=401")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.service").isEqualTo("monolith");
    }

    @Test
    void pathOutsideApi_shouldNotBeRouted() {
        client.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isNotFound();
    }
}
