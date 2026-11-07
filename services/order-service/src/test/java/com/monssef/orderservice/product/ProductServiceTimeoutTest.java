package com.monssef.orderservice.product;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.orderservice.client.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// product-service answers, but too late: the read timeout gives a 503 instead of holding the request
@SpringBootTest(classes = ProductServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false",
                // Shorter than in application.yml, so the test does not wait 5 s
                "spring.cloud.openfeign.client.config.default.read-timeout=300"})
class ProductServiceTimeoutTest {

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

    @Test
    void findAll_shouldThrow503_whenProductServiceAnswersTooLate() {
        productService.stubFor(get(urlPathEqualTo("/internal/products"))
                .willReturn(okJson("[]").withFixedDelay(2000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.findAll(List.of(1L)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
        // A timeout is not retried: a hanging product-service would hang again
        productService.verify(1, getRequestedFor(urlPathEqualTo("/internal/products")));
    }

    @Test
    void reserve_shouldNotSendAgain_whenProductServiceAnswersTooLate() {
        // The stock may be reserved by the time the answer comes: a second request could reserve it twice
        productService.stubFor(post("/internal/stock/reservations").willReturn(okJson("[]").withFixedDelay(2000)));

        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ServiceUnavailableException.class);
        productService.verify(1, postRequestedFor(urlEqualTo("/internal/stock/reservations")));
    }
}
