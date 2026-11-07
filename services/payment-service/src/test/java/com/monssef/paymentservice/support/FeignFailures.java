package com.monssef.paymentservice.support;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// What Feign throws when a call fails before any answer: the IOException is the cause of a RetryableException
public final class FeignFailures {

    private FeignFailures() {
    }

    // The service is not listening (restarting): the request never left
    public static FeignException connectionRefused() {
        return failure(new ConnectException("Connection refused"));
    }

    // The container is stopped but still listed in Eureka: the request never left
    public static FeignException unknownHost() {
        return failure(new UnknownHostException("order-service"));
    }

    // The request was sent, the connection dropped before the answer
    public static FeignException connectionReset() {
        return failure(new SocketException("Connection reset"));
    }

    // The request was sent, no answer within the read timeout
    public static FeignException readTimeout() {
        return failure(new SocketTimeoutException("Read timed out"));
    }

    private static FeignException failure(IOException cause) {
        Request request = Request.create(Request.HttpMethod.POST, "http://order-service/internal/orders/7/paid",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.POST, cause, (Long) null, request);
    }
}
