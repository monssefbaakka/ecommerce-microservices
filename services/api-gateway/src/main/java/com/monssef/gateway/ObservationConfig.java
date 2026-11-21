package com.monssef.gateway;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * Requests that are not worth a trace: the health checks of Docker (every 10 s) and the calls of the
 * Eureka client (heartbeat, registry fetch, every few seconds). Each one would be a trace of its own in
 * Zipkin, burying the requests of the users. The requests routed to the services are always traced.
 */
@Configuration(proxyBeanMethods = false)
public class ObservationConfig {

    @Bean
    public ObservationPredicate noObservationForInfrastructureCalls() {
        return (name, context) -> {
            // The gateway runs on WebFlux: the reactive variant of the server context
            if (context instanceof ServerRequestObservationContext server) {
                return !server.getCarrier().getPath().value().startsWith("/actuator");
            }
            // The Eureka client sends its calls with a RestClient, even in a WebFlux application
            if (context instanceof ClientRequestObservationContext client && client.getCarrier() != null) {
                return !client.getCarrier().getURI().getPath().startsWith("/eureka");
            }
            return true;
        };
    }
}
