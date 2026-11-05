package com.monssef.orderservice.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 503 when another service is down, not registered in Eureka, or answers with an error the user cannot fix
public class ServiceUnavailableException extends ResponseStatusException {

    // service: "Product service" gives "Product service unavailable"
    public ServiceUnavailableException(String service) {
        super(HttpStatus.SERVICE_UNAVAILABLE, service + " unavailable");
    }
}
