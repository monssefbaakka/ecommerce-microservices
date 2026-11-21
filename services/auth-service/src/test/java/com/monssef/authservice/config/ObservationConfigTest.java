package com.monssef.authservice.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationConfigTest {

    private final ObservationPredicate predicate = new ObservationConfig().noObservationForInfrastructureCalls();

    @Test
    void apiRequest_shouldBeObserved() {
        assertThat(observed(serverRequest("/api/cart"))).isTrue();
        assertThat(observed(serverRequest("/internal/orders/7"))).isTrue();
    }

    @Test
    void actuatorRequests_shouldNotBeObserved() {
        // Health checks of Docker and Eureka, scrapes of Prometheus
        assertThat(observed(serverRequest("/actuator/health/readiness"))).isFalse();
        assertThat(observed(serverRequest("/actuator/prometheus"))).isFalse();
    }

    @Test
    void callToAnotherService_shouldBeObserved() {
        assertThat(observed(clientRequest("http://product-service:8082/internal/products?ids=1"))).isTrue();
    }

    @Test
    void callsOfTheEurekaClient_shouldNotBeObserved() {
        assertThat(observed(clientRequest("http://discovery-server:8761/eureka/apps/ORDER-SERVICE/order-service:8083"))).isFalse();
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
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", path), new MockHttpServletResponse());
    }

    private static ClientRequestObservationContext clientRequest(String url) {
        return new ClientRequestObservationContext(new MockClientHttpRequest(HttpMethod.GET, URI.create(url)));
    }
}
