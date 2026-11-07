package com.monssef.paymentservice.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Every call to another service goes through here: a circuit breaker around a retry around the call.
 * - The circuit breaker counts a call once, after its retries: with one instance down out of two,
 *   half the attempts fail but every call succeeds on the other one, and it must stay closed.
 *   Once it is open, the calls fail at once with a 503, without any attempt or timeout, until it
 *   lets a few calls through to test the service again.
 * - Reads and writes have their own retry: a read is sent again after any short-lived failure,
 *   a write only when it never reached the service (RetryableReadFailure, RequestNeverSent).
 * A 404 / 409 of the other service goes through untouched; any other failure ends as a 503.
 */
@Slf4j
public class RemoteCalls {

    private final String service;
    private final CircuitBreaker breaker;
    private final Retry readRetry;
    private final Retry writeRetry;

    // service: the name shown in the 503 message, e.g. "Order service"
    public RemoteCalls(String service, CircuitBreaker breaker, Retry readRetry, Retry writeRetry) {
        this.service = service;
        this.breaker = breaker;
        this.readRetry = readRetry;
        this.writeRetry = writeRetry;
        logEvents();
    }

    // A GET: asking twice changes nothing on the other side
    public <T> T read(Supplier<T> call) {
        return run(readRetry, call);
    }

    // A POST: sent again only if it never left, so the other side never does it twice
    public <T> T write(Supplier<T> call) {
        return run(writeRetry, call);
    }

    // One line when the circuit breaker opens or closes, one per attempt sent again
    private void logEvents() {
        breaker.getEventPublisher().onStateTransition(event -> log.warn("Circuit breaker of {}: {}",
                event.getCircuitBreakerName(), event.getStateTransition()));
        for (Retry retry : new Retry[]{readRetry, writeRetry}) {
            retry.getEventPublisher().onRetry(event -> log.info("{}: attempt {} failed, sent again in {} ms ({})",
                    event.getName(), event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis(),
                    event.getLastThrowable().getMessage()));
        }
    }

    private <T> T run(Retry retry, Supplier<T> call) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(breaker, Retry.decorateSupplier(retry, call));
        try {
            return guarded.get();
        } catch (CallNotPermittedException e) {
            log.warn("{} not called: its circuit breaker is {}", breaker.getName(), breaker.getState());
            throw new ServiceUnavailableException(service);
        } catch (FeignException e) {
            // Connection refused, timeout or unreadable answer. The error answers are already turned
            // into a ResponseStatusException by the decoder, and go through untouched
            log.warn("{} call failed: {}", breaker.getName(), e.getMessage());
            throw new ServiceUnavailableException(service);
        }
    }
}
