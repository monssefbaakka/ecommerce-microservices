package com.monssef.orderservice.common;

import com.monssef.orderservice.order.CancelReason;
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
        assertThat(registry.get("orders.cancelled").tag("reason", "customer").counter().count()).isZero();
        assertThat(registry.get("orders.cancelled").tag("reason", "expired").counter().count()).isZero();
        assertThat(registry.get("cart.fallback").counter().count()).isZero();
    }

    @Test
    void orderPlaced_shouldCountEachOrder() {
        metrics.orderPlaced();
        metrics.orderPlaced();

        assertThat(registry.get("orders.placed").counter().count()).isEqualTo(2);
    }

    @Test
    void orderCancelled_shouldCountEachCancellationByReason() {
        metrics.orderCancelled(CancelReason.CUSTOMER);
        metrics.orderCancelled(CancelReason.EXPIRED);
        metrics.orderCancelled(CancelReason.EXPIRED);

        assertThat(registry.get("orders.cancelled").tag("reason", "customer").counter().count()).isEqualTo(1);
        assertThat(registry.get("orders.cancelled").tag("reason", "expired").counter().count()).isEqualTo(2);
        assertThat(registry.get("orders.placed").counter().count()).isZero();
    }

    @Test
    void cartShownFromLastKnownProducts_shouldCountEachFallback() {
        metrics.cartShownFromLastKnownProducts();

        assertThat(registry.get("cart.fallback").counter().count()).isEqualTo(1);
        assertThat(registry.get("orders.placed").counter().count()).isZero();
    }
}
