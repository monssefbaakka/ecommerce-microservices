package com.monssef.paymentservice.client;

import com.monssef.paymentservice.support.FeignFailures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class RequestNeverSentTest {

    private final RequestNeverSent neverSent = new RequestNeverSent();

    @Test
    void shouldRetry_whenTheConnectionIsRefused() {
        assertThat(neverSent.test(FeignFailures.connectionRefused())).isTrue();
    }

    @Test
    void shouldRetry_whenTheHostIsUnknown() {
        assertThat(neverSent.test(FeignFailures.unknownHost())).isTrue();
    }

    @Test
    void shouldNotRetry_whenTheRequestMayHaveBeenReceived() {
        // The order may already be PAID: sent again, it would answer 409
        assertThat(neverSent.test(FeignFailures.connectionReset())).isFalse();
        assertThat(neverSent.test(FeignFailures.readTimeout())).isFalse();
        assertThat(neverSent.test(new ServiceUnavailableException("Order service"))).isFalse();
    }

    @Test
    void shouldNotRetry_anAnswerOfOrderService() {
        assertThat(neverSent.test(new ResponseStatusException(HttpStatus.CONFLICT, "Order 7 is PAID"))).isFalse();
    }
}
