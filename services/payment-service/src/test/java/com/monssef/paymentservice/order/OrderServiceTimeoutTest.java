package com.monssef.paymentservice.order;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.paymentservice.client.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// order-service answers, but too late: the read timeout gives a 503 instead of holding the payment
@SpringBootTest(classes = OrderServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false",
                // Shorter than in application.yml, so the test does not wait 5 s
                "spring.cloud.openfeign.client.config.default.read-timeout=300"})
class OrderServiceTimeoutTest {

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

    @Test
    void find_shouldThrow503_whenOrderServiceAnswersTooLate() {
        orderService.stubFor(get("/internal/orders/7")
                .willReturn(okJson("{\"id\":7}").withFixedDelay(2000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.find(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
        // A timeout is not retried: a hanging order-service would hang again
        orderService.verify(1, getRequestedFor(urlEqualTo("/internal/orders/7")));
    }

    @Test
    void markPaid_shouldNotSendAgain_whenOrderServiceAnswersTooLate() {
        // The order may be PAID by the time the answer comes: a second request would answer 409
        orderService.stubFor(post("/internal/orders/7/paid").willReturn(okJson("{\"id\":7}").withFixedDelay(2000)));

        assertThatThrownBy(() -> client.markPaid(7L)).isInstanceOf(ServiceUnavailableException.class);
        orderService.verify(1, postRequestedFor(urlEqualTo("/internal/orders/7/paid")));
    }
}
