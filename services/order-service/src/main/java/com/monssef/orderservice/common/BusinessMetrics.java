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
    private final Counter cartFallbacks;

    public BusinessMetrics(MeterRegistry registry) {
        ordersPlaced = Counter.builder("orders.placed")
                .description("Orders placed: stock reserved in product-service and order saved")
                .register(registry);
        cartFallbacks = Counter.builder("cart.fallback")
                .description("Carts shown with the last known products because product-service was unavailable")
                .register(registry);
    }

    public void orderPlaced() {
        ordersPlaced.increment();
    }

    // Rising while product-service is down: the users see names and prices that may be out of date
    public void cartShownFromLastKnownProducts() {
        cartFallbacks.increment();
    }
}
