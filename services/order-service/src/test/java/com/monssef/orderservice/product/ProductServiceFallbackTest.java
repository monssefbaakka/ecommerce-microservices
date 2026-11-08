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

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The fallback of the cart over real HTTP, behind the retries and the circuit breaker of application.yml
@SpringBootTest(classes = ProductServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false",
                // No wait between the read attempts: the test sends many reads to a failing product-service
                "resilience4j.retry.instances.product-service-reads.wait-duration=1ms"})
class ProductServiceFallbackTest {

    private static final String PRODUCTS = "/internal/products";
    private static final String IPHONE = "[{\"id\":1,\"name\":\"iPhone 15\",\"price\":999.99,\"stock\":5}]";

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
    void shouldReturnTheLastAnswer_whenProductServiceFailsAfterwards() {
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).willReturn(okJson(IPHONE)));
        assertThat(client.findAllOrLastKnown(List.of(1L)).upToDate()).isTrue();

        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).willReturn(serverError()));
        ProductSnapshot snapshot = client.findAllOrLastKnown(List.of(1L));

        assertThat(snapshot.upToDate()).isFalse();
        assertThat(snapshot.products().get(1L).name()).isEqualTo("iPhone 15");
    }

    @Test
    void shouldReturnTheLastAnswerWithoutCallingProductService_onceTheCircuitBreakerIsOpen() {
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).willReturn(okJson(IPHONE)));
        client.findAll(List.of(1L));
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).willReturn(serverError()));
        for (int i = 0; i < 5; i++) {
            client.findAllOrLastKnown(List.of(1L));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        productService.resetRequests();

        ProductSnapshot snapshot = client.findAllOrLastKnown(List.of(1L));

        assertThat(snapshot.upToDate()).isFalse();
        assertThat(snapshot.products()).containsOnlyKeys(1L);
        productService.verify(0, getRequestedFor(urlPathEqualTo(PRODUCTS)));
    }

    @Test
    void findAll_shouldStillThrow503_whenProductServiceFails() {
        // Placing an order needs the current prices: no fallback there
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).willReturn(okJson(IPHONE)));
        client.findAll(List.of(1L));
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).willReturn(serverError()));

        assertThatThrownBy(() -> client.findAll(List.of(1L))).isInstanceOf(ServiceUnavailableException.class);
    }
}
