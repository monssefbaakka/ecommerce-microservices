package com.monssef.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// A service that answers too late: the gateway gives a 504 instead of holding the request
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false",
                // Shorter than in application.yml, so the test does not wait 15 s
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms"})
class GatewayTimeoutTest {

    private static final StubService PRODUCT_SERVICE = new StubService("product");

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void productService(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", PRODUCT_SERVICE::url);
    }

    @AfterAll
    static void stopStub() {
        PRODUCT_SERVICE.close();
    }

    @Test
    void slowService_shouldReturn504InTheCommonFormat_atTheResponseTimeout() {
        long start = System.nanoTime();

        client.get().uri("/api/products?delay=3000")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.error").isEqualTo("Gateway Timeout")
                .jsonPath("$.message").isEqualTo("Service did not answer in time")
                .jsonPath("$.path").isEqualTo("/api/products")
                .jsonPath("$.errors").isEmpty();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void serviceAnsweringWithinTheTimeout_shouldGoThrough() {
        client.get().uri("/api/products?delay=100")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("product");
    }
}
