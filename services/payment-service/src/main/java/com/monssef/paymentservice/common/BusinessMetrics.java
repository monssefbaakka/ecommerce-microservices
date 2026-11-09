package com.monssef.paymentservice.common;

import com.monssef.paymentservice.payment.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Business counters of payment-service, next to the technical metrics Spring Boot already publishes
 * (HTTP requests, JVM, connection pool, Resilience4j). Registered at startup: /actuator/metrics lists
 * them at 0 before the first event, so a dashboard can tell "nothing happened" from "not measured".
 */
@Component
public class BusinessMetrics {

    // One counter per outcome, under the same name: /actuator/metrics/payments?tag=status:declined
    private final Map<PaymentStatus, Counter> payments = new EnumMap<>(PaymentStatus.class);

    public BusinessMetrics(MeterRegistry registry) {
        for (PaymentStatus status : PaymentStatus.values()) {
            payments.put(status, Counter.builder("payments")
                    .description("Payments recorded, by outcome of the card")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry));
        }
    }

    // A sudden share of "declined" points at the card processor rather than at the users
    public void paymentRecorded(PaymentStatus status) {
        payments.get(status).increment();
    }
}
