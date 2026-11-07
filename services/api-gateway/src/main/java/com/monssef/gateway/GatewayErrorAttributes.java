package com.monssef.gateway;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Errors raised by the gateway itself (no route, service down or too slow) get the same JSON as the errors
 * of the services: {status, error, message, path, errors}. Errors returned by a service pass through as is.
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable error = getError(request);
        HttpStatus status = statusOf(error);

        // "status" also sets the HTTP status of the response
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("status", status.value());
        attributes.put("error", status.getReasonPhrase());
        attributes.put("message", messageOf(error, status));
        attributes.put("path", request.path());
        attributes.put("errors", List.of());
        return attributes;
    }

    private HttpStatus statusOf(Throwable error) {
        // The service has an address (Eureka or fixed URL) but nothing answers there
        if (isUnreachable(error) || isUnreachable(NestedExceptionUtils.getRootCause(error))) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (error instanceof ResponseStatusException statusError) {
            HttpStatus status = HttpStatus.resolve(statusError.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private boolean isUnreachable(Throwable error) {
        return error instanceof ConnectException || error instanceof UnknownHostException;
    }

    private String messageOf(Throwable error, HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "No route for this path";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            // "Unable to find instance for auth-service" when no instance is registered in Eureka
            return error instanceof ResponseStatusException statusError && statusError.getReason() != null
                    ? statusError.getReason()
                    : "Service unavailable";
        }
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            // Past the response-timeout of application.yml: the service is up but did not answer
            return "Service did not answer in time";
        }
        if (status.is5xxServerError()) {
            // The exception message may reveal internal addresses: logs only
            return "Une erreur inattendue est survenue";
        }
        return status.getReasonPhrase();
    }
}
