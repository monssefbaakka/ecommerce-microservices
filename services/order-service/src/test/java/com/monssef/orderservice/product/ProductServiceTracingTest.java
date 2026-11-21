package com.monssef.orderservice.product;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.monssef.orderservice.auth.ServiceTokenProvider;
import com.monssef.orderservice.common.BusinessMetrics;
import com.monssef.orderservice.config.ResilienceConfig;
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

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A call to product-service made while a request of the user is being served carries the trace of that
 * request (W3C "traceparent" header): product-service continues the same trace, and Zipkin shows both
 * services under one trace id.
 */
@SpringBootTest(classes = ProductServiceTracingTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                "management.endpoint.health.validate-group-membership=false"})
// The tests turn tracing off unless asked: without it no trace is propagated at all. Nothing is sent to
// Zipkin either way (management.tracing.export.zipkin.enabled is false by default in application.yml)
@AutoConfigureTracing
class ProductServiceTracingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class})
    @EnableFeignClients(clients = ProductServiceApi.class)
    @Import({ProductClient.class, LastKnownProducts.class, BusinessMetrics.class, ServiceTokenProvider.class,
            ResilienceConfig.class})
    static class FeignClientOnly {
    }

    // W3C trace context: version, trace id (32 hex), id of the span of the call (16 hex), flags (01 = sampled)
    private static final String TRACEPARENT = "00-([0-9a-f]{32})-([0-9a-f]{16})-01";

    @RegisterExtension
    static WireMockExtension productService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerProductService(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", productService::baseUrl);
    }

    @Autowired
    private ProductServiceApi api;

    @Autowired
    private Tracer tracer;

    @Test
    void callToProductService_shouldContinueTheTraceOfTheCurrentRequest() {
        productService.stubFor(get(urlPathEqualTo("/internal/products")).willReturn(okJson("[]")));

        // What the server side of order-service does for each incoming request
        Span request = tracer.nextSpan().name("GET /api/cart").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            api.findAllById(List.of(1L));
        } finally {
            request.end();
        }

        LoggedRequest call = productService.findAll(getRequestedFor(urlPathEqualTo("/internal/products"))).getFirst();
        String traceparent = call.getHeader("traceparent");
        assertThat(traceparent).matches(TRACEPARENT);
        assertThat(traceparent.split("-")[1]).isEqualTo(request.context().traceId());
        // The call has a span of its own, child of the request: Zipkin shows how long product-service took
        assertThat(traceparent.split("-")[2]).isNotEqualTo(request.context().spanId());
    }

    @Test
    void callOutsideAnyRequest_shouldStartATraceOfItsOwn() {
        productService.stubFor(get(urlPathEqualTo("/internal/products")).willReturn(okJson("[]")));

        api.findAllById(List.of(1L));

        LoggedRequest call = productService.findAll(getRequestedFor(urlPathEqualTo("/internal/products"))).getFirst();
        assertThat(call.getHeader("traceparent")).matches(TRACEPARENT);
    }
}
