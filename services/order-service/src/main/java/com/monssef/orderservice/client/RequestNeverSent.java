package com.monssef.orderservice.client;

import feign.FeignException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.function.Predicate;

/**
 * Which failed writes (POST) are sent again: only those that never reached the other service.
 * Connection refused (the service is restarting) or unknown host (its container is stopped but
 * still listed in Eureka). Reserving the stock twice is not harmless, so a write that may have
 * been received (timeout, dropped connection, 5xx) is never sent again.
 * Named in application.yml (retry-exception-predicate), built by Resilience4j.
 */
public class RequestNeverSent implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        return failure instanceof FeignException
                && Causes.contain(failure, ConnectException.class, UnknownHostException.class);
    }
}
