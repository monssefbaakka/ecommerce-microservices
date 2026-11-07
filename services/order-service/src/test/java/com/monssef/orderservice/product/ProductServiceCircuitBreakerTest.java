package com.monssef.orderservice.product;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.orderservice.client.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The circuit breaker of application.yml over real HTTP: once open, product-service receives nothing
@SpringBootTest(classes = ProductServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false",
                // Shorter than in application.yml, so the test does not wait 10 s for the half-open state
                "resilience4j.circuitbreaker.instances.product-service.wait-duration-in-open-state=300ms"})
class ProductServiceCircuitBreakerTest {

    private static final String RESERVATIONS = "/internal/stock/reservations";
    private static final String RESERVED = "[{\"productId\":1,\"name\":\"iPhone 15\",\"price\":999.99,\"quantity\":2}]";

    @RegisterExtension
    static WireMockExtension productService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerProductService(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", productService::baseUrl);
    }

    @Autowired
    private ProductClient client;

    @Autowired
    private CircuitBreakerRegistry breakers;

    private CircuitBreaker breaker;

    @BeforeEach
    void closeTheCircuitBreaker() {
        breaker = breakers.circuitBreaker("product-service");
        breaker.reset();
    }

    @Test
    void shouldOpenAfterFiveFailedCalls_andThenAnswer503WithoutCallingProductService() {
        productService.stubFor(post(RESERVATIONS).willReturn(serverError()));
        for (int i = 0; i < 5; i++) {
            reserveExpecting503();
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        long start = System.nanoTime();
        reserveExpecting503();
        assertThatThrownBy(() -> client.findAll(List.of(1L))).isInstanceOf(ServiceUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(200));
        productService.verify(5, postRequestedFor(urlEqualTo(RESERVATIONS)));
        productService.verify(0, getRequestedFor(urlPathEqualTo("/internal/products")));
    }

    @Test
    void shouldStayClosed_whenProductServiceAnswers409() {
        // Not enough stock is the user's problem: product-service works fine
        productService.stubFor(post(RESERVATIONS).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":409,\"message\":\"Not enough stock for product 1 (iPhone 15): 1 left\"}")));

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                    .isInstanceOf(ResponseStatusException.class)
                    .isNotInstanceOf(ServiceUnavailableException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        productService.verify(10, postRequestedFor(urlEqualTo(RESERVATIONS)));
    }

    @Test
    void shouldCloseAgain_onceProductServiceIsBack() throws InterruptedException {
        productService.stubFor(post(RESERVATIONS).willReturn(serverError()));
        for (int i = 0; i < 5; i++) {
            reserveExpecting503();
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        productService.stubFor(post(RESERVATIONS).willReturn(okJson(RESERVED)));
        Thread.sleep(400);   // past the wait in the open state: a few calls are let through to test it

        for (int i = 0; i < 3; i++) {
            assertThat(client.reserve(List.of(new StockLine(1L, 2)))).hasSize(1);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private void reserveExpecting503() {
        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable");
    }
}
