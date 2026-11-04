package com.monssef.paymentservice.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.paymentservice.auth.ServiceTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Calls the /internal endpoints of order-service, which owns the orders.
 * Each call carries a SERVICE token. A 404 or 409 of order-service is passed on with its message;
 * order-service down, not registered or failing gives a 503.
 */
@Slf4j
@Component
public class OrderClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OrderClient(RestClient.Builder loadBalancedBuilder, ServiceTokenProvider tokens, ObjectMapper objectMapper,
                       @Value("${services.order-service-url}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = loadBalancedBuilder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokens.token());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw toException(response);
                })
                .build();
    }

    // Owner, status and total of the order: 404 if unknown
    public OrderSummary find(Long id) {
        return call(() -> restClient.get()
                .uri("/internal/orders/{id}", id)
                .retrieve()
                .body(OrderSummary.class));
    }

    // PENDING -> PAID once the card is accepted: 409 if the order is not PENDING any more
    public OrderSummary markPaid(Long id) {
        return call(() -> restClient.post()
                .uri("/internal/orders/{id}/paid", id)
                .retrieve()
                .body(OrderSummary.class));
    }

    // Connection refused, timeout, or no instance in Eureka (IllegalStateException of the load balancer)
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("order-service call failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    private ResponseStatusException toException(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.value() == HttpStatus.NOT_FOUND.value() || status.value() == HttpStatus.CONFLICT.value()) {
            return new ResponseStatusException(status, messageOf(response));
        }
        log.warn("order-service answered {}", status.value());
        return unavailable();
    }

    // The ErrorResponse of order-service: { "status", "error", "message", ... }
    private String messageOf(ClientHttpResponse response) {
        try {
            JsonNode body = objectMapper.readTree(response.getBody());
            return body.path("message").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order service unavailable");
    }
}
