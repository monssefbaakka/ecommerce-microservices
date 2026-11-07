package com.monssef.paymentservice.order;

import com.monssef.paymentservice.client.RemoteCalls;
import com.monssef.paymentservice.client.ServiceUnavailableException;
import com.monssef.paymentservice.support.FeignFailures;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// The Feign client is mocked: what goes over HTTP is tested in OrderServiceApiTest,
// the circuit breaker and the retries in RemoteCallsTest (here: one attempt, never opened)
@ExtendWith(MockitoExtension.class)
class OrderClientTest {

    private static final OrderSummary PENDING =
            new OrderSummary(7L, "john@example.com", OrderStatus.PENDING, new BigDecimal("1999.98"));

    @Mock
    private OrderServiceApi api;

    private OrderClient client;

    @BeforeEach
    void setUp() {
        Retry once = Retry.of("once", RetryConfig.custom().maxAttempts(1).build());
        client = new OrderClient(api, new RemoteCalls("Order service", CircuitBreaker.ofDefaults("order-service"),
                once, once));
    }

    @Test
    void find_shouldReturnOwnerStatusAndTotal() {
        when(api.find(7L)).thenReturn(PENDING);

        assertThat(client.find(7L)).isEqualTo(PENDING);
    }

    @Test
    void markPaid_shouldReturnThePaidOrder() {
        OrderSummary paid = new OrderSummary(7L, "john@example.com", OrderStatus.PAID, new BigDecimal("1999.98"));
        when(api.markPaid(7L)).thenReturn(paid);

        assertThat(client.markPaid(7L).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void find_shouldLetThe404OfTheDecoderThrough() {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id 99 not found");
        when(api.find(99L)).thenThrow(notFound);

        assertThatThrownBy(() -> client.find(99L)).isSameAs(notFound);
    }

    @Test
    void markPaid_shouldLetThe409OfTheDecoderThrough() {
        ResponseStatusException conflict = new ResponseStatusException(HttpStatus.CONFLICT,
                "Order 7 is PAID, only a PENDING order can be paid");
        when(api.markPaid(7L)).thenThrow(conflict);

        assertThatThrownBy(() -> client.markPaid(7L)).isSameAs(conflict);
    }

    @Test
    void find_shouldThrow503_whenOrderServiceIsUnreachable() {
        when(api.find(7L)).thenThrow(FeignFailures.connectionRefused());

        assertUnavailable(() -> client.find(7L));
    }

    @Test
    void markPaid_shouldThrow503_whenOrderServiceIsUnreachable() {
        when(api.markPaid(7L)).thenThrow(FeignFailures.connectionRefused());

        assertUnavailable(() -> client.markPaid(7L));
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
