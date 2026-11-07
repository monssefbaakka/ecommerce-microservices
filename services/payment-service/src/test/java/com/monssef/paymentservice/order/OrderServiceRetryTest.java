package com.monssef.paymentservice.order;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.paymentservice.client.ServiceUnavailableException;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The retries of application.yml over real HTTP: how many requests the fake order-service receives
@SpringBootTest(classes = OrderServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class OrderServiceRetryTest {

    private static final String ORDER = "/internal/orders/7";
    private static final String PAID = "/internal/orders/7/paid";

    @RegisterExtension
    static WireMockExtension orderService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerOrderService(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", orderService::baseUrl);
    }

    @Autowired
    private OrderClient client;

    @Autowired
    private CircuitBreakerRegistry breakers;

    @BeforeEach
    void closeTheCircuitBreaker() {
        // Shared by the tests of this class: the failures of one must not open it for the next
        breakers.circuitBreaker("order-service").reset();
    }

    @Test
    void find_shouldSendAgain_whenOrderServiceFailsOnce() {
        orderService.stubFor(get(ORDER).inScenario("restart").whenScenarioStateIs(STARTED)
                .willReturn(serverError()).willSetStateTo("up"));
        orderService.stubFor(get(ORDER).inScenario("restart").whenScenarioStateIs("up")
                .willReturn(okJson("{\"id\":7,\"userEmail\":\"john@example.com\",\"status\":\"PENDING\",\"total\":1999.98}")));

        assertThat(client.find(7L).status()).isEqualTo(OrderStatus.PENDING);
        orderService.verify(2, getRequestedFor(urlEqualTo(ORDER)));
    }

    @Test
    void find_shouldGiveUpAfterThreeAttempts_with503() {
        orderService.stubFor(get(ORDER).willReturn(serverError()));

        assertThatThrownBy(() -> client.find(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
        orderService.verify(3, getRequestedFor(urlEqualTo(ORDER)));
    }

    @Test
    void find_shouldNotSendAgain_a404() {
        orderService.stubFor(get(ORDER).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":404,\"message\":\"Order with id 7 not found\"}")));

        assertThatThrownBy(() -> client.find(7L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        orderService.verify(1, getRequestedFor(urlEqualTo(ORDER)));
    }

    @Test
    void markPaid_shouldNotSendAgain_whenOrderServiceFails() {
        // order-service may have marked the order PAID before failing: a second request would answer 409
        orderService.stubFor(post(PAID).willReturn(serverError()));

        assertThatThrownBy(() -> client.markPaid(7L)).isInstanceOf(ServiceUnavailableException.class);
        orderService.verify(1, postRequestedFor(urlEqualTo(PAID)));
    }

    @Test
    void markPaid_shouldNotSendAgain_whenTheConnectionDrops() {
        orderService.stubFor(post(PAID).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.markPaid(7L)).isInstanceOf(ServiceUnavailableException.class);
        orderService.verify(1, postRequestedFor(urlEqualTo(PAID)));
    }
}
