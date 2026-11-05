package com.monssef.paymentservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Turns an error answer of another service into the exception this service answers with.
 * A 404 or 409 is the user's problem (unknown order, order not PENDING any more): passed on with its message.
 * Anything else (401 / 403 between services, 5xx, no instance in Eureka) is a 503.
 */
@Slf4j
public class RemoteErrorDecoder implements ErrorDecoder {

    private final String service;
    private final ObjectMapper objectMapper;

    // service: the name shown in the 503 message and in the logs, e.g. "Order service"
    public RemoteErrorDecoder(String service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.CONFLICT.value()) {
            return new ResponseStatusException(HttpStatus.valueOf(status), messageOf(response));
        }
        log.warn("{} answered {} to {}", service, status, methodKey);
        return new ServiceUnavailableException(service);
    }

    // The ErrorResponse of the other service: { "status", "error", "message", ... }
    private String messageOf(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream body = response.body().asInputStream()) {
            return objectMapper.readTree(body).path("message").asText(null);
        } catch (IOException e) {
            return null;
        }
    }
}
