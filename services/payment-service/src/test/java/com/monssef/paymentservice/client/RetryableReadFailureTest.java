package com.monssef.paymentservice.client;

import com.monssef.paymentservice.support.FeignFailures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class RetryableReadFailureTest {

    private final RetryableReadFailure retryable = new RetryableReadFailure();

    @Test
    void shouldRetry_whenTheConnectionIsRefusedOrDropped() {
        assertThat(retryable.test(FeignFailures.connectionRefused())).isTrue();
        assertThat(retryable.test(FeignFailures.unknownHost())).isTrue();
        assertThat(retryable.test(FeignFailures.connectionReset())).isTrue();
    }

    @Test
    void shouldRetry_whenOrderServiceFailsOrIsNotRegistered() {
        // What the decoder gives for a 5xx or the 503 of the LoadBalancer
        assertThat(retryable.test(new ServiceUnavailableException("Order service"))).isTrue();
    }

    @Test
    void shouldNotRetry_aTimeout() {
        // Three attempts would hold the user for three read timeouts
        assertThat(retryable.test(FeignFailures.readTimeout())).isFalse();
    }

    @Test
    void shouldNotRetry_anAnswerOfOrderService() {
        assertThat(retryable.test(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id 99 not found"))).isFalse();
        assertThat(retryable.test(new ResponseStatusException(HttpStatus.CONFLICT, "Order 7 is PAID"))).isFalse();
    }

    @Test
    void shouldNotRetry_aBugOfThisService() {
        assertThat(retryable.test(new IllegalStateException("bug"))).isFalse();
    }
}
