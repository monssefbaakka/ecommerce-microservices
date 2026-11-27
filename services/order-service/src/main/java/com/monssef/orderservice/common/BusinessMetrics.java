package com.monssef.orderservice.common;

import com.monssef.orderservice.order.CancelReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Business counters of order-service, next to the technical metrics Spring Boot already publishes
 * (HTTP requests, JVM, connection pool, Resilience4j). Registered at startup: /actuator/metrics lists
 * them at 0 before the first event, so a dashboard can tell "nothing happened" from "not measured".
 */
@Component
public class BusinessMetrics {

    private final Counter ordersPlaced;
    private final Map<CancelReason, Counter> ordersCancelled = new EnumMap<>(CancelReason.class);
    private final Counter cartFallbacks;

    public BusinessMetrics(MeterRegistry registry) {
        ordersPlaced = Counter.builder("orders.placed")
                .description("Orders placed: stock reserved in product-service and order saved")
                .register(registry);
        for (CancelReason reason : CancelReason.values()) {
            ordersCancelled.put(reason, Counter.builder("orders.cancelled")
                    .description("Orders cancelled and their stock given back, by the user or once expired unpaid")
                    .tag("reason", reason.name().toLowerCase(Locale.ROOT))
                    .register(registry));
        }
        cartFallbacks = Counter.builder("cart.fallback")
                .description("Carts shown with the last known products because product-service was unavailable")
                .register(registry);
    }

    public void orderPlaced() {
        ordersPlaced.increment();
    }

    public void orderCancelled(CancelReason reason) {
        ordersCancelled.get(reason).increment();
    }

    // Rising while product-service is down: the users see names and prices that may be out of date
    public void cartShownFromLastKnownProducts() {
        cartFallbacks.increment();
    }
}
