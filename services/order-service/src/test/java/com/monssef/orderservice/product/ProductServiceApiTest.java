package com.monssef.orderservice.product;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.orderservice.client.ServiceUnavailableException;
import com.monssef.orderservice.auth.ServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Feign client over real HTTP, against a fake product-service (WireMock). "product-service" is
 * resolved by the LoadBalancer like in production, from a static list instead of Eureka.
 */
@SpringBootTest(classes = ProductServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class ProductServiceApiTest {

    /**
     * Just the Feign client of product-service and what it needs (Jackson, LoadBalancer, discovery):
     * no database, no web server, no Eureka. Nested in a test class, so the integration tests never scan it.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class})
    @EnableFeignClients(clients = ProductServiceApi.class)
    @Import({ProductClient.class, ServiceTokenProvider.class})
    static class FeignClientOnly {
    }

    private static final String STOCK_REQUEST = "{\"items\":[{\"productId\":1,\"quantity\":2}]}";

    @RegisterExtension
    static WireMockExtension productService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerProductService(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", productService::baseUrl);
    }

    @Autowired
    private FeignClientProperties feignProperties;

    @Autowired
    private ProductServiceApi api;

    @Autowired
    private ProductClient client;

    @Test
    void findAllById_shouldAskForEveryIdInOneCall_withServiceToken() {
        productService.stubFor(get(urlPathEqualTo("/internal/products"))
                .willReturn(okJson("""
                        [{"id":1,"name":"iPhone 15","description":"Phone","price":999.99,"stock":10},
                         {"id":2,"name":"Case","description":null,"price":19.90,"stock":0}]
                        """)));

        List<ProductInfo> products = api.findAllById(List.of(1L, 2L));

        assertThat(products).containsExactly(
                new ProductInfo(1L, "iPhone 15", new BigDecimal("999.99"), 10),
                new ProductInfo(2L, "Case", new BigDecimal("19.90"), 0));
        productService.verify(getRequestedFor(urlPathEqualTo("/internal/products"))
                .withQueryParam("ids", havingExactly("1", "2"))
                .withHeader("Authorization", matching("Bearer ey.+")));
    }

    @Test
    void reserve_shouldPostTheLinesAndReturnWhatWasReserved() {
        productService.stubFor(post("/internal/stock/reservations")
                .willReturn(okJson("""
                        [{"productId":1,"name":"iPhone 15","price":999.99,"quantity":2}]
                        """)));

        List<ReservedItem> reserved = api.reserve(new StockRequest(List.of(new StockLine(1L, 2))));

        assertThat(reserved).containsExactly(new ReservedItem(1L, "iPhone 15", new BigDecimal("999.99"), 2));
        productService.verify(postRequestedFor(urlEqualTo("/internal/stock/reservations"))
                .withHeader("Authorization", matching("Bearer ey.+"))
                .withRequestBody(equalToJson(STOCK_REQUEST)));
    }

    @Test
    void release_shouldPostTheLines() {
        productService.stubFor(post("/internal/stock/releases").willReturn(noContent()));

        api.release(new StockRequest(List.of(new StockLine(1L, 2))));

        productService.verify(postRequestedFor(urlEqualTo("/internal/stock/releases"))
                .withRequestBody(equalToJson(STOCK_REQUEST)));
    }

    @Test
    void reserve_shouldPassOn409WithTheMessageOfProductService() {
        productService.stubFor(post("/internal/stock/reservations")
                .willReturn(jsonError(409, """
                        {"status":409,"error":"Conflict",
                         "message":"Not enough stock for product 1 (iPhone 15): 1 left","path":"/internal/stock/reservations"}
                        """)));

        assertThatThrownBy(() -> api.reserve(new StockRequest(List.of(new StockLine(1L, 2)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough stock for product 1 (iPhone 15): 1 left")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reserve_shouldPassOn404_whenProductDeleted() {
        productService.stubFor(post("/internal/stock/reservations")
                .willReturn(jsonError(404, "{\"status\":404,\"message\":\"Product with id 1 not found\"}")));

        assertThatThrownBy(() -> api.reserve(new StockRequest(List.of(new StockLine(1L, 2)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product with id 1 not found")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reserve_shouldThrow503_whenProductServiceFails() {
        productService.stubFor(post("/internal/stock/reservations").willReturn(serverError()));

        assertThatThrownBy(() -> api.reserve(new StockRequest(List.of(new StockLine(1L, 2)))))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable");
    }

    @Test
    void findAll_shouldThrow503_whenTheConnectionDrops() {
        productService.stubFor(get(urlPathEqualTo("/internal/products"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.findAll(List.of(1L)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable");
    }

    @Test
    void timeouts_shouldComeFromApplicationYml_insteadOfTheFeignDefaults() {
        FeignClientProperties.FeignClientConfiguration defaults = feignProperties.getConfig().get("default");

        // 10 s / 60 s without them: a hanging product-service would hold the request for a minute
        assertThat(defaults.getConnectTimeout()).isEqualTo(2000);
        assertThat(defaults.getReadTimeout()).isEqualTo(5000);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonError(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }
}
