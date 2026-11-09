package com.monssef.orderservice.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business counters of order-service, next to the technical metrics Spring Boot already publishes
 * (HTTP requests, JVM, connection pool, Resilience4j). Registered at startup: /actuator/metrics lists
 * them at 0 before the first event, so a dashboard can tell "nothing happened" from "not measured".
 */
@Component
public class BusinessMetrics {

    private final Counter ordersPlaced;

    public BusinessMetrics(MeterRegistry registry) {
        ordersPlaced = Counter.builder("orders.placed")
                .description("Orders placed: stock reserved in product-service and order saved")
                .register(registry);
    }

    public void orderPlaced() {
        ordersPlaced.increment();
    }
}
