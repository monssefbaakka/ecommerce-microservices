package com.monssef.orderservice.product;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.orderservice.client.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The retries of application.yml over real HTTP: how many requests the fake product-service receives
@SpringBootTest(classes = ProductServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class ProductServiceRetryTest {

    private static final String PRODUCTS = "/internal/products";
    private static final String RESERVATIONS = "/internal/stock/reservations";

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

    @BeforeEach
    void closeTheCircuitBreaker() {
        // Shared by the tests of this class: the failures of one must not open it for the next
        breakers.circuitBreaker("product-service").reset();
    }

    @Test
    void findAll_shouldSendAgain_whenProductServiceFailsOnce() {
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).inScenario("restart").whenScenarioStateIs(STARTED)
                .willReturn(serverError()).willSetStateTo("up"));
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).inScenario("restart").whenScenarioStateIs("up")
                .willReturn(okJson("[{\"id\":1,\"name\":\"iPhone 15\",\"price\":999.99,\"stock\":10}]")));

        assertThat(client.findAll(List.of(1L))).containsOnlyKeys(1L);
        productService.verify(2, getRequestedFor(urlPathEqualTo(PRODUCTS)));
    }

    @Test
    void findAll_shouldGiveUpAfterThreeAttempts_with503() {
        productService.stubFor(get(urlPathEqualTo(PRODUCTS)).willReturn(serverError()));

        assertThatThrownBy(() -> client.findAll(List.of(1L)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable");
        productService.verify(3, getRequestedFor(urlPathEqualTo(PRODUCTS)));
    }

    @Test
    void findAll_shouldSendAgain_whenTheConnectionDrops() {
        productService.stubFor(get(urlPathEqualTo(PRODUCTS))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.findAll(List.of(1L))).isInstanceOf(ServiceUnavailableException.class);
        // 3 attempts. The HttpURLConnection under Feign also resends a GET once by itself when the
        // connection drops, so product-service sees up to 6 (never a POST: see the reserve tests)
        productService.verify(moreThanOrExactly(3), getRequestedFor(urlPathEqualTo(PRODUCTS)));
    }

    @Test
    void reserve_shouldNotSendAgain_whenProductServiceFails() {
        // product-service may have reserved the stock before failing: a second request could reserve it twice
        productService.stubFor(post(RESERVATIONS).willReturn(serverError()));

        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ServiceUnavailableException.class);
        productService.verify(1, postRequestedFor(urlEqualTo(RESERVATIONS)));
    }

    @Test
    void reserve_shouldNotSendAgain_whenTheConnectionDrops() {
        productService.stubFor(post(RESERVATIONS).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ServiceUnavailableException.class);
        productService.verify(1, postRequestedFor(urlEqualTo(RESERVATIONS)));
    }

    @Test
    void reserve_shouldNotSendAgain_a409() {
        productService.stubFor(post(RESERVATIONS).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":409,\"message\":\"Not enough stock for product 1 (iPhone 15): 1 left\"}")));

        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        productService.verify(1, postRequestedFor(urlEqualTo(RESERVATIONS)));
    }
}
