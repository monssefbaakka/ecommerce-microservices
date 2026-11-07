package com.monssef.paymentservice.client;

import com.monssef.paymentservice.support.FeignFailures;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Real circuit breaker and retries, with a small window and no wait: the HTTP side is tested against WireMock
@ExtendWith(MockitoExtension.class)
class RemoteCallsTest {

    private final CircuitBreaker breaker = CircuitBreaker.of("order-service", CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .recordExceptions(FeignException.class, ServiceUnavailableException.class)
            .build());

    private final RemoteCalls calls = new RemoteCalls("Order service", breaker,
            retry("order-service-reads", new RetryableReadFailure()),
            retry("order-service-writes", new RequestNeverSent()));

    @Mock
    private Supplier<String> call;

    @Test
    void read_shouldReturnTheAnswer() {
        when(call.get()).thenReturn("answer");

        assertThat(calls.read(call)).isEqualTo("answer");
    }

    @Test
    void read_shouldSendAgain_afterAShortLivedFailure() {
        when(call.get()).thenThrow(new ServiceUnavailableException("Order service")).thenReturn("answer");

        assertThat(calls.read(call)).isEqualTo("answer");
        verify(call, times(2)).get();
    }

    @Test
    void read_shouldGiveUpAfterThreeAttempts_with503() {
        when(call.get()).thenThrow(FeignFailures.connectionRefused());

        assertUnavailable(() -> calls.read(call));
        verify(call, times(3)).get();
    }

    @Test
    void read_shouldNotSendAgain_afterATimeout() {
        when(call.get()).thenThrow(FeignFailures.readTimeout());

        assertUnavailable(() -> calls.read(call));
        verify(call).get();
    }

    @Test
    void read_shouldPassOnA404Untouched_withoutSendingAgain() {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id 99 not found");
        when(call.get()).thenThrow(notFound);

        assertThatThrownBy(() -> calls.read(call)).isSameAs(notFound);
        verify(call).get();
    }

    @Test
    void write_shouldSendAgain_whenTheConnectionWasRefused() {
        when(call.get()).thenThrow(FeignFailures.connectionRefused()).thenReturn("paid");

        assertThat(calls.write(call)).isEqualTo("paid");
        verify(call, times(2)).get();
    }

    @Test
    void write_shouldNotSendAgain_whenTheRequestMayHaveBeenReceived() {
        when(call.get()).thenThrow(FeignFailures.connectionReset());

        assertUnavailable(() -> calls.write(call));
        verify(call).get();
    }

    @Test
    void write_shouldNotSendAgain_whenOrderServiceFails() {
        when(call.get()).thenThrow(new ServiceUnavailableException("Order service"));

        assertUnavailable(() -> calls.write(call));
        verify(call).get();
    }

    @Test
    void circuitBreaker_shouldOpenWhenHalfTheCallsFail_andThenNotCallOrderServiceAtAll() {
        Supplier<String> failing = () -> {
            throw new ServiceUnavailableException("Order service");
        };
        for (int i = 0; i < 4; i++) {
            assertUnavailable(() -> calls.write(failing));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertUnavailable(() -> calls.read(call));
        verifyNoInteractions(call);
    }

    @Test
    void circuitBreaker_shouldCountACallOnce_afterItsRetries() {
        when(call.get()).thenThrow(FeignFailures.connectionRefused());

        for (int i = 0; i < 3; i++) {
            assertUnavailable(() -> calls.read(call));
        }
        // 9 failed attempts, but only 3 failed calls: not enough to judge yet
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        assertUnavailable(() -> calls.read(call));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(call, times(12)).get();
    }

    @Test
    void circuitBreaker_shouldStayClosed_whenTheRetriesSucceed() {
        // One instance down out of two: every other attempt is refused, every call succeeds
        when(call.get()).thenThrow(FeignFailures.connectionRefused()).thenReturn("answer")
                .thenThrow(FeignFailures.connectionRefused()).thenReturn("answer")
                .thenThrow(FeignFailures.connectionRefused()).thenReturn("answer")
                .thenThrow(FeignFailures.connectionRefused()).thenReturn("answer");

        for (int i = 0; i < 4; i++) {
            assertThat(calls.read(call)).isEqualTo("answer");
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitBreaker_shouldStayClosed_on404And409() {
        // An order already paid is an answer: order-service works fine
        Supplier<String> conflict = () -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order 7 is PAID");
        };
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> calls.write(conflict)).isInstanceOf(ResponseStatusException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static Retry retry(String name, Predicate<Throwable> retryable) {
        return Retry.of(name, RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryOnException(retryable)
                .build());
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
    }
}
