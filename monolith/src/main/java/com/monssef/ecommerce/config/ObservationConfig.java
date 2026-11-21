package com.monssef.ecommerce.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Requests that are not worth a trace: the health checks (Docker every 10 s) and the Prometheus scrapes.
 * Each one would be a trace of its own in Zipkin, burying the requests of the users. They are left out of
 * the http.server.requests metrics too, which then only count real traffic.
 */
@Configuration(proxyBeanMethods = false)
public class ObservationConfig {

    @Bean
    public ObservationPredicate noObservationForInfrastructureCalls() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext server) {
                return !server.getCarrier().getRequestURI().startsWith("/actuator");
            }
            return true;
        };
    }
}
