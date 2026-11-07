package com.monssef.orderservice.product;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two instances of product-service in the LoadBalancer, one stopped (nothing listens on its port):
 * what happens while Eureka still lists an instance that just went down. The LoadBalancer takes them
 * in turn, so a refused attempt is sent again to the other one, and the user never sees it.
 */
@SpringBootTest(classes = ProductServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class ProductServiceInstanceDownTest {

    private static final String RESERVATIONS = "/internal/stock/reservations";

    @RegisterExtension
    static WireMockExtension productService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerTwoInstances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri",
                () -> "http://localhost:" + freePort());
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[1].uri", productService::baseUrl);
    }

    @Autowired
    private ProductClient client;

    // Repeated: the LoadBalancer starts with either instance, both orders must work
    @RepeatedTest(4)
    void reserve_shouldReachTheLiveInstance_andReserveOnlyOnce() {
        productService.resetRequests();
        productService.stubFor(post(RESERVATIONS)
                .willReturn(okJson("[{\"productId\":1,\"name\":\"iPhone 15\",\"price\":999.99,\"quantity\":2}]")));

        assertThat(client.reserve(List.of(new StockLine(1L, 2)))).hasSize(1);
        productService.verify(1, postRequestedFor(urlEqualTo(RESERVATIONS)));
    }

    @RepeatedTest(4)
    void findAll_shouldReachTheLiveInstance() {
        productService.stubFor(get(urlPathEqualTo("/internal/products"))
                .willReturn(okJson("[{\"id\":1,\"name\":\"iPhone 15\",\"price\":999.99,\"stock\":10}]")));

        assertThat(client.findAll(List.of(1L))).containsOnlyKeys(1L);
    }

    // A port nothing listens on once the socket is closed: connecting to it is refused
    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
