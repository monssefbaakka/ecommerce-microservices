package com.monssef.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is where a trace starts: every request it routes reaches the service with a W3C
 * "traceparent" header, so the service and the calls it makes join the same trace in Zipkin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
@AutoConfigureWebTestClient
// The tests turn tracing off unless asked. Nothing is sent to Zipkin either way
// (management.tracing.export.zipkin.enabled is false by default in application.yml)
@AutoConfigureTracing
class GatewayTracingTest {

    // Version, trace id (32 hex), id of the parent span (16 hex), flags (01 = sampled)
    private static final String TRACEPARENT = "00-([0-9a-f]{32})-([0-9a-f]{16})-01";

    private static final StubService ORDER_SERVICE = new StubService("order");

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void downstreamServices(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", ORDER_SERVICE::url);
    }

    @AfterAll
    static void stopStub() {
        ORDER_SERVICE.close();
    }

    @Test
    void requestWithoutTrace_shouldReachTheServiceWithANewTrace() {
        String traceparent = traceparentReceivedForCart(null);

        assertThat(traceparent).matches(TRACEPARENT);
    }

    @Test
    void eachRequest_shouldGetATraceOfItsOwn() {
        String first = traceparentReceivedForCart(null);
        String second = traceparentReceivedForCart(null);

        assertThat(traceIdOf(first)).isNotEqualTo(traceIdOf(second));
    }

    @Test
    void requestWithTrace_shouldContinueItInTheService() {
        // e.g. sent by a browser or a client that traces its own calls
        String fromClient = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        String traceparent = traceparentReceivedForCart(fromClient);

        assertThat(traceparent).matches(TRACEPARENT);
        assertThat(traceIdOf(traceparent)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        // The gateway's own span sits in between: Zipkin shows the time spent in the gateway itself
        assertThat(traceparent.split("-")[2]).isNotEqualTo("00f067aa0ba902b7");
    }

    private String traceparentReceivedForCart(String traceparent) {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri("/api/cart");
        if (traceparent != null) {
            request = request.header("traceparent", traceparent);
        }
        String body = request.exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).contains("\"service\": \"order\"");
        return body.replaceAll("(?s).*\"traceparent\": \"([^\"]*)\".*", "$1");
    }

    private static String traceIdOf(String traceparent) {
        return traceparent.split("-")[1];
    }
}
