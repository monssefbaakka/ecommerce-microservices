package com.monssef.paymentservice.common;

import com.monssef.paymentservice.payment.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// In-memory registry: no Spring context, the counters are read back directly
class BusinessMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BusinessMetrics metrics = new BusinessMetrics(registry);

    @Test
    void counters_shouldExistAtZeroForEveryOutcome() {
        assertThat(payments("succeeded")).isZero();
        assertThat(payments("declined")).isZero();
    }

    @Test
    void paymentRecorded_shouldCountByOutcome() {
        metrics.paymentRecorded(PaymentStatus.SUCCEEDED);
        metrics.paymentRecorded(PaymentStatus.DECLINED);
        metrics.paymentRecorded(PaymentStatus.DECLINED);

        assertThat(payments("succeeded")).isEqualTo(1);
        assertThat(payments("declined")).isEqualTo(2);
        assertThat(registry.get("payments").counters()).hasSize(2); // one series per outcome, no other tag
    }

    private double payments(String status) {
        return registry.get("payments").tag("status", status).counter().count();
    }
}
