package com.monssef.paymentservice.order;

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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two instances of order-service in the LoadBalancer, one stopped (nothing listens on its port):
 * what happens while Eureka still lists an instance that just went down. The LoadBalancer takes them
 * in turn, so a refused attempt is sent again to the other one, and the user never sees it.
 */
@SpringBootTest(classes = OrderServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class OrderServiceInstanceDownTest {

    private static final String PAID = "/internal/orders/7/paid";
    private static final String ORDER_JSON = "{\"id\":7,\"userEmail\":\"john@example.com\",\"status\":\"%s\",\"total\":1999.98}";

    @RegisterExtension
    static WireMockExtension orderService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerTwoInstances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri",
                () -> "http://localhost:" + freePort());
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[1].uri", orderService::baseUrl);
    }

    @Autowired
    private OrderClient client;

    // Repeated: the LoadBalancer starts with either instance, both orders must work
    @RepeatedTest(4)
    void markPaid_shouldReachTheLiveInstance_andMarkPaidOnlyOnce() {
        orderService.resetRequests();
        orderService.stubFor(post(PAID).willReturn(okJson(ORDER_JSON.formatted("PAID"))));

        assertThat(client.markPaid(7L).status()).isEqualTo(OrderStatus.PAID);
        orderService.verify(1, postRequestedFor(urlEqualTo(PAID)));
    }

    @RepeatedTest(4)
    void find_shouldReachTheLiveInstance() {
        orderService.stubFor(get("/internal/orders/7").willReturn(okJson(ORDER_JSON.formatted("PENDING"))));

        assertThat(client.find(7L).status()).isEqualTo(OrderStatus.PENDING);
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
