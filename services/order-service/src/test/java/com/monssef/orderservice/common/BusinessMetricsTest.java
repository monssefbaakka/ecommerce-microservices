package com.monssef.orderservice.common;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// In-memory registry: no Spring context, the counters are read back directly
class BusinessMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BusinessMetrics metrics = new BusinessMetrics(registry);

    @Test
    void counters_shouldExistAtZeroBeforeTheFirstEvent() {
        assertThat(registry.get("orders.placed").counter().count()).isZero();
    }

    @Test
    void orderPlaced_shouldCountEachOrder() {
        metrics.orderPlaced();
        metrics.orderPlaced();

        assertThat(registry.get("orders.placed").counter().count()).isEqualTo(2);
    }
}
