package com.monssef.orderservice.client;

import com.monssef.orderservice.support.FeignFailures;
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
        // The stock may already be reserved: sending it again could reserve it twice
        assertThat(neverSent.test(FeignFailures.connectionReset())).isFalse();
        assertThat(neverSent.test(FeignFailures.readTimeout())).isFalse();
        assertThat(neverSent.test(new ServiceUnavailableException("Product service"))).isFalse();
    }

    @Test
    void shouldNotRetry_anAnswerOfProductService() {
        assertThat(neverSent.test(new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock"))).isFalse();
    }
}
