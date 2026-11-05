package com.monssef.orderservice.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;

/**
 * Adds a fresh SERVICE token to each Feign call to the /internal endpoints of another service.
 * Never the token of the user: the other service only trusts the SERVICE role there.
 */
public class ServiceTokenInterceptor implements RequestInterceptor {

    private final ServiceTokenProvider tokens;

    public ServiceTokenInterceptor(ServiceTokenProvider tokens) {
        this.tokens = tokens;
    }

    @Override
    public void apply(RequestTemplate request) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
    }
}
