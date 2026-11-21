package com.monssef.ecommerce.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationConfigTest {

    private final ObservationPredicate predicate = new ObservationConfig().noObservationForInfrastructureCalls();

    @Test
    void apiRequest_shouldBeObserved() {
        assertThat(observed(serverRequest("/api/cart"))).isTrue();
        assertThat(observed(serverRequest("/api/products/1"))).isTrue();
    }

    @Test
    void actuatorRequests_shouldNotBeObserved() {
        // Health checks of Docker, scrapes of Prometheus
        assertThat(observed(serverRequest("/actuator/health/readiness"))).isFalse();
        assertThat(observed(serverRequest("/actuator/prometheus"))).isFalse();
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
}
