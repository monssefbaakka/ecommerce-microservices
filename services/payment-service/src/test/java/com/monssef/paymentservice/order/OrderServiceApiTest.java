package com.monssef.paymentservice.order;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.paymentservice.auth.ServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Feign client over real HTTP, against a fake order-service (WireMock). "order-service" is
 * resolved by the LoadBalancer like in production, from a static list instead of Eureka.
 */
@SpringBootTest(classes = OrderServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class OrderServiceApiTest {

    /**
     * Just the Feign client of order-service and what it needs (Jackson, LoadBalancer, discovery):
     * no database, no web server, no Eureka. Nested in a test class, so the integration tests never scan it.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class})
    @EnableFeignClients(clients = OrderServiceApi.class)
    @Import({OrderClient.class, ServiceTokenProvider.class})
    static class FeignClientOnly {
    }

    private static final String ORDER_JSON = """
            {"id":7,"userEmail":"john@example.com","status":"%s","total":1999.98}
            """;

    @RegisterExtension
    static WireMockExtension orderService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerOrderService(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", orderService::baseUrl);
    }

    @Autowired
    private OrderServiceApi api;

    @Autowired
    private OrderClient client;

    @Test
    void find_shouldReturnOwnerStatusAndTotal_withServiceToken() {
        orderService.stubFor(get("/internal/orders/7").willReturn(okJson(ORDER_JSON.formatted("PENDING"))));

        OrderSummary order = api.find(7L);

        assertThat(order).isEqualTo(
                new OrderSummary(7L, "john@example.com", OrderStatus.PENDING, new BigDecimal("1999.98")));
        orderService.verify(getRequestedFor(urlEqualTo("/internal/orders/7"))
                .withHeader("Authorization", matching("Bearer ey.+")));
    }

    @Test
    void markPaid_shouldPostAndReturnThePaidOrder() {
        orderService.stubFor(post("/internal/orders/7/paid").willReturn(okJson(ORDER_JSON.formatted("PAID"))));

        assertThat(api.markPaid(7L).status()).isEqualTo(OrderStatus.PAID);
        orderService.verify(postRequestedFor(urlEqualTo("/internal/orders/7/paid"))
                .withHeader("Authorization", matching("Bearer ey.+")));
    }
}
