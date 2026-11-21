package com.monssef.gateway;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationConfigTest {

    private final ObservationPredicate predicate = new ObservationConfig().noObservationForInfrastructureCalls();

    @Test
    void routedRequest_shouldBeObserved() {
        assertThat(observed(serverRequest("/api/cart"))).isTrue();
        assertThat(observed(serverRequest("/api/products/1"))).isTrue();
    }

    @Test
    void healthCheck_shouldNotBeObserved() {
        assertThat(observed(serverRequest("/actuator/health/readiness"))).isFalse();
    }

    @Test
    void callsOfTheEurekaClient_shouldNotBeObserved() {
        assertThat(observed(clientRequest("http://discovery-server:8761/eureka/apps/API-GATEWAY/api-gateway:8090"))).isFalse();
        assertThat(observed(clientRequest("http://discovery-server:8761/eureka/apps/delta"))).isFalse();
    }

    @Test
    void otherObservations_shouldBeKept() {
        assertThat(observed(new Observation.Context())).isTrue();
    }

    private boolean observed(Observation.Context context) {
        return predicate.test("any", context);
    }

    private static ServerRequestObservationContext serverRequest(String path) {
        return new ServerRequestObservationContext(MockServerHttpRequest.get(path).build(), new MockServerHttpResponse(), Map.of());
    }

    private static ClientRequestObservationContext clientRequest(String url) {
        return new ClientRequestObservationContext(new MockClientHttpRequest(HttpMethod.GET, URI.create(url)));
    }
}
