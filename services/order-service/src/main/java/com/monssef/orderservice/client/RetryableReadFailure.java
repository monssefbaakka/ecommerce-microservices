package com.monssef.orderservice.client;

import feign.FeignException;

import java.net.SocketTimeoutException;
import java.util.function.Predicate;

/**
 * Which failed reads (GET) are sent again. Asking twice changes nothing on the other side, so any
 * failure that may be short-lived is retried: connection refused or dropped, 5xx, no instance in
 * Eureka yet (the decoder gives a ServiceUnavailableException for those).
 * Not a timeout: a service that hangs would hang again, and three attempts would hold the user
 * for 15 s instead of 5 s. Not a 404 / 409 either: that is an answer.
 * Named in application.yml (retry-exception-predicate), built by Resilience4j.
 */
public class RetryableReadFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        if (failure instanceof ServiceUnavailableException) {
            return true;
        }
        return failure instanceof FeignException && !Causes.contain(failure, SocketTimeoutException.class);
    }
}
