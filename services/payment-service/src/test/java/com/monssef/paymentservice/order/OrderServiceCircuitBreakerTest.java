package com.monssef.paymentservice.order;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.paymentservice.client.ServiceUnavailableException;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The circuit breaker of application.yml over real HTTP: once open, order-service receives nothing
@SpringBootTest(classes = OrderServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false",
                // Shorter than in application.yml, so the test does not wait 10 s for the half-open state
                "resilience4j.circuitbreaker.instances.order-service.wait-duration-in-open-state=300ms"})
class OrderServiceCircuitBreakerTest {

    private static final String PAID = "/internal/orders/7/paid";
    private static final String PAID_ORDER = "{\"id\":7,\"userEmail\":\"john@example.com\",\"status\":\"PAID\",\"total\":1999.98}";

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

    private CircuitBreaker breaker;

    @BeforeEach
    void closeTheCircuitBreaker() {
        breaker = breakers.circuitBreaker("order-service");
        breaker.reset();
    }

    @Test
    void shouldOpenAfterFiveFailedCalls_andThenAnswer503WithoutCallingOrderService() {
        orderService.stubFor(post(PAID).willReturn(serverError()));
        for (int i = 0; i < 5; i++) {
            markPaidExpecting503();
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        long start = System.nanoTime();
        markPaidExpecting503();
        assertThatThrownBy(() -> client.find(7L)).isInstanceOf(ServiceUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(200));
        orderService.verify(5, postRequestedFor(urlEqualTo(PAID)));
        orderService.verify(0, getRequestedFor(urlEqualTo("/internal/orders/7")));
    }

    @Test
    void shouldStayClosed_whenOrderServiceAnswers409() {
        // An order already paid is the user's problem: order-service works fine
        orderService.stubFor(post(PAID).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":409,\"message\":\"Order 7 is PAID, only a PENDING order can be paid\"}")));

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> client.markPaid(7L))
                    .isInstanceOf(ResponseStatusException.class)
                    .isNotInstanceOf(ServiceUnavailableException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        orderService.verify(10, postRequestedFor(urlEqualTo(PAID)));
    }

    @Test
    void shouldCloseAgain_onceOrderServiceIsBack() throws InterruptedException {
        orderService.stubFor(post(PAID).willReturn(serverError()));
        for (int i = 0; i < 5; i++) {
            markPaidExpecting503();
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        orderService.stubFor(post(PAID).willReturn(okJson(PAID_ORDER)));
        Thread.sleep(400);   // past the wait in the open state: a few calls are let through to test it

        for (int i = 0; i < 3; i++) {
            assertThat(client.markPaid(7L).status()).isEqualTo(OrderStatus.PAID);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private void markPaidExpecting503() {
        assertThatThrownBy(() -> client.markPaid(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
    }
}
