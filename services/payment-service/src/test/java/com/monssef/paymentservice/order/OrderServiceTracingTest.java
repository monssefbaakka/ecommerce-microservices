package com.monssef.paymentservice.order;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.monssef.paymentservice.auth.ServiceTokenProvider;
import com.monssef.paymentservice.config.ResilienceConfig;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A call to order-service made while a payment request is being served carries the trace of that
 * request (W3C "traceparent" header): order-service continues the same trace, and Zipkin shows both
 * services under one trace id.
 */
@SpringBootTest(classes = OrderServiceTracingTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                "management.endpoint.health.validate-group-membership=false"})
// The tests turn tracing off unless asked: without it no trace is propagated at all. Nothing is sent to
// Zipkin either way (management.tracing.export.zipkin.enabled is false by default in application.yml)
@AutoConfigureTracing
class OrderServiceTracingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class})
    @EnableFeignClients(clients = OrderServiceApi.class)
    @Import({OrderClient.class, ServiceTokenProvider.class, ResilienceConfig.class})
    static class FeignClientOnly {
    }

    // W3C trace context: version, trace id (32 hex), id of the span of the call (16 hex), flags (01 = sampled)
    private static final String TRACEPARENT = "00-([0-9a-f]{32})-([0-9a-f]{16})-01";

    private static final String ORDER_JSON = """
            {"id":7,"userEmail":"john@example.com","status":"PENDING","total":1999.98}
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
    private Tracer tracer;

    @Test
    void callToOrderService_shouldContinueTheTraceOfTheCurrentRequest() {
        orderService.stubFor(get("/internal/orders/7").willReturn(okJson(ORDER_JSON)));

        // What the server side of payment-service does for each incoming request
        Span request = tracer.nextSpan().name("POST /api/payments").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            api.find(7L);
        } finally {
            request.end();
        }

        LoggedRequest call = orderService.findAll(getRequestedFor(urlEqualTo("/internal/orders/7"))).getFirst();
        String traceparent = call.getHeader("traceparent");
        assertThat(traceparent).matches(TRACEPARENT);
        assertThat(traceparent.split("-")[1]).isEqualTo(request.context().traceId());
        // The call has a span of its own, child of the request: Zipkin shows how long order-service took
        assertThat(traceparent.split("-")[2]).isNotEqualTo(request.context().spanId());
    }

    @Test
    void callOutsideAnyRequest_shouldStartATraceOfItsOwn() {
        orderService.stubFor(get("/internal/orders/7").willReturn(okJson(ORDER_JSON)));

        api.find(7L);

        LoggedRequest call = orderService.findAll(getRequestedFor(urlEqualTo("/internal/orders/7"))).getFirst();
        assertThat(call.getHeader("traceparent")).matches(TRACEPARENT);
    }
}
