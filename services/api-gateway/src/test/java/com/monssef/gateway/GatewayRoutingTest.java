package com.monssef.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The real gateway on a random port, in front of fake services.
 * No Eureka server: the services are declared in the simple discovery client,
 * which lb:// also reads.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class GatewayRoutingTest {

    private static final StubService AUTH_SERVICE = new StubService("auth");
    private static final StubService PRODUCT_SERVICE = new StubService("product");
    private static final StubService ORDER_SERVICE = new StubService("order");
    private static final StubService PAYMENT_SERVICE = new StubService("payment");
    private static final StubService MONOLITH = new StubService("monolith");

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void downstreamServices(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", AUTH_SERVICE::url);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", PRODUCT_SERVICE::url);
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", ORDER_SERVICE::url);
        registry.add("spring.cloud.discovery.client.simple.instances.payment-service[0].uri", PAYMENT_SERVICE::url);
        registry.add("MONOLITH_URL", MONOLITH::url);
    }

    @AfterAll
    static void stopStubs() {
        AUTH_SERVICE.close();
        PRODUCT_SERVICE.close();
        ORDER_SERVICE.close();
        PAYMENT_SERVICE.close();
        MONOLITH.close();
    }

    @Test
    void login_shouldGoToAuthServiceWithPathAndBodyUnchanged() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"john@example.com\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("auth")
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.path").isEqualTo("/api/auth/login")
                .jsonPath("$.body").isEqualTo("{\"email\":\"john@example.com\"}");
    }

    @Test
    void register_shouldGoToAuthService() {
        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("auth")
                .jsonPath("$.path").isEqualTo("/api/auth/register");
    }

    @Test
    void products_shouldGoToProductServiceWithQueryUnchanged() {
        client.get().uri("/api/products?name=iphone&sort=price,desc")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("product")
                .jsonPath("$.path").isEqualTo("/api/products");
    }

    @Test
    void categories_shouldGoToProductService() {
        client.delete().uri("/api/categories/3")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("product")
                .jsonPath("$.method").isEqualTo("DELETE")
                .jsonPath("$.path").isEqualTo("/api/categories/3")
                .jsonPath("$.authorization").isEqualTo("Bearer admin-token");
    }

    @Test
    void cart_shouldGoToOrderService() {
        client.get().uri("/api/cart")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order")
                .jsonPath("$.path").isEqualTo("/api/cart");
    }

    @Test
    void cartItems_shouldGoToOrderService() {
        client.put().uri("/api/cart/items/3")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"quantity\":2}")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order")
                .jsonPath("$.method").isEqualTo("PUT")
                .jsonPath("$.path").isEqualTo("/api/cart/items/3");
    }

    @Test
    void orders_shouldGoToOrderService() {
        client.post().uri("/api/orders")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order")
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.path").isEqualTo("/api/orders");
        client.get().uri("/api/orders/7")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order")
                .jsonPath("$.path").isEqualTo("/api/orders/7");
    }

    @Test
    void payments_shouldGoToPaymentServiceWithBodyUnchanged() {
        client.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":7}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("payment")
                .jsonPath("$.path").isEqualTo("/api/payments")
                .jsonPath("$.body").isEqualTo("{\"orderId\":7}");
        client.get().uri("/api/payments/3")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("payment")
                .jsonPath("$.path").isEqualTo("/api/payments/3");
    }

    @Test
    void otherApiPaths_shouldStillGoToTheMonolith() {
        client.get().uri("/api/something-else")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("monolith")
                .jsonPath("$.path").isEqualTo("/api/something-else");
    }

    @Test
    void authorizationHeader_shouldReachTheService() {
        client.get().uri("/api/cart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-token")
                .exchange()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order")
                .jsonPath("$.authorization").isEqualTo("Bearer some-token");
    }

    @Test
    void serviceStatus_shouldBeReturnedAsIs() {
        // e.g. order-service's 401 for a cart without token: the gateway does not check tokens itself
        client.get().uri("/api/cart?status=401")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order");
    }

    @Test
    void pathOutsideApi_shouldNotBeRouted() {
        client.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isNotFound();
    }
}
