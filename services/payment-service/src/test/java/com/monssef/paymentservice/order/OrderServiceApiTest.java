package com.monssef.paymentservice.order;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.monssef.paymentservice.auth.ServiceTokenProvider;
import com.monssef.paymentservice.client.ServiceUnavailableException;
import com.monssef.paymentservice.config.ResilienceConfig;
import com.monssef.paymentservice.support.FeignFailures;
import feign.Logger;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
     * Just the Feign client of order-service and what it needs (Jackson, LoadBalancer, discovery,
     * the circuit breaker and retries of application.yml): no database, no web server, no Eureka. Nested in a test class, so the integration tests never scan it.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class})
    @EnableFeignClients(clients = OrderServiceApi.class)
    @Import({OrderClient.class, ServiceTokenProvider.class, ResilienceConfig.class})
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
    private FeignClientProperties feignProperties;

    @Autowired
    private OrderServiceApi api;

    @Autowired
    private OrderClient client;

    @Autowired
    private CircuitBreakerRegistry breakers;

    @Autowired
    private RetryRegistry retries;

    @BeforeEach
    void closeTheCircuitBreaker() {
        // Shared by the tests of this class: the failures of one must not open it for the next
        breakers.circuitBreaker("order-service").reset();
    }

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

    @Test
    void find_shouldPassOn404WithTheMessageOfOrderService() {
        orderService.stubFor(get("/internal/orders/99")
                .willReturn(jsonError(404, """
                        {"status":404,"error":"Not Found",
                         "message":"Order with id 99 not found","path":"/internal/orders/99"}
                        """)));

        assertThatThrownBy(() -> api.find(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order with id 99 not found")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markPaid_shouldPassOn409_whenOrderNotPending() {
        orderService.stubFor(post("/internal/orders/7/paid")
                .willReturn(jsonError(409,
                        "{\"status\":409,\"message\":\"Order 7 is PAID, only a PENDING order can be paid\"}")));

        // Through OrderClient: the decoder's answer must not be turned into a 503 on the way
        assertThatThrownBy(() -> client.markPaid(7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order 7 is PAID, only a PENDING order can be paid")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void find_shouldThrow503_whenOrderServiceFails() {
        orderService.stubFor(get("/internal/orders/7").willReturn(serverError()));

        assertThatThrownBy(() -> api.find(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
    }

    @Test
    void find_shouldThrow503_whenOrderServiceRejectsTheToken() {
        orderService.stubFor(get("/internal/orders/7").willReturn(forbidden()));

        assertThatThrownBy(() -> api.find(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
    }

    @Test
    void markPaid_shouldThrow503_whenTheConnectionDrops() {
        orderService.stubFor(post("/internal/orders/7/paid")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.markPaid(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
    }

    @Test
    void timeouts_shouldComeFromApplicationYml_insteadOfTheFeignDefaults() {
        FeignClientProperties.FeignClientConfiguration defaults = feignProperties.getConfig().get("default");

        // 10 s / 60 s without them: a hanging order-service would hold the request for a minute
        assertThat(defaults.getConnectTimeout()).isEqualTo(2000);
        assertThat(defaults.getReadTimeout()).isEqualTo(5000);
    }

    @Test
    void loggerLevel_shouldBeBasic_soTheServiceTokenIsNeverLogged() {
        // HEADERS or FULL would write the Authorization header of every call to the logs
        assertThat(feignProperties.getConfig().get("default").getLoggerLevel()).isEqualTo(Logger.Level.BASIC);
    }

    @Test
    void circuitBreaker_shouldComeFromApplicationYml() {
        CircuitBreakerConfig config = breakers.circuitBreaker("order-service").getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(3));
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10_000L);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        // Recorded: order-service unwell. Not recorded: its 404 / 409 answers
        assertThat(config.getRecordExceptionPredicate().test(FeignFailures.readTimeout())).isTrue();
        assertThat(config.getRecordExceptionPredicate().test(new ServiceUnavailableException("Order service"))).isTrue();
        assertThat(config.getRecordExceptionPredicate()
                .test(new ResponseStatusException(HttpStatus.CONFLICT, "Order 7 is PAID"))).isFalse();
    }

    @Test
    void retries_shouldComeFromApplicationYml_withTheirOwnRuleForReadsAndWrites() {
        RetryConfig reads = retries.retry("order-service-reads").getRetryConfig();
        RetryConfig writes = retries.retry("order-service-writes").getRetryConfig();

        for (RetryConfig config : List.of(reads, writes)) {
            assertThat(config.getMaxAttempts()).isEqualTo(3);
            // 200 ms before the 2nd attempt, 400 ms before the 3rd
            assertThat(config.getIntervalBiFunction().apply(1, null)).isEqualTo(200L);
            assertThat(config.getIntervalBiFunction().apply(2, null)).isEqualTo(400L);
        }
        assertThat(reads.getExceptionPredicate().test(FeignFailures.connectionReset())).isTrue();
        assertThat(writes.getExceptionPredicate().test(FeignFailures.connectionReset())).isFalse();
        assertThat(writes.getExceptionPredicate().test(FeignFailures.connectionRefused())).isTrue();
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonError(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }
}
