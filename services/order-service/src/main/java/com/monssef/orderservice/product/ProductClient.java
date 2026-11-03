package com.monssef.orderservice.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.orderservice.auth.ServiceTokenProvider;
import com.monssef.orderservice.common.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Calls the /internal endpoints of product-service, which owns the products and their stock.
 * Each call carries a SERVICE token. A 404 or 409 of product-service is passed on with its message;
 * product-service down, not registered or failing gives a 503.
 */
@Slf4j
@Component
public class ProductClient {

    private static final ParameterizedTypeReference<List<ProductInfo>> PRODUCTS = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ReservedItem>> RESERVED = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProductClient(RestClient.Builder loadBalancedBuilder, ServiceTokenProvider tokens, ObjectMapper objectMapper,
                         @Value("${services.product-service-url}") String baseUrl) {
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

    // Current name, price and stock, by id. A product deleted since is simply missing from the map
    public Map<Long, ProductInfo> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> products = call(() -> restClient.get()
                .uri(uri -> uri.path("/internal/products").queryParam("ids", ids.toArray()).build())
                .retrieve()
                .body(PRODUCTS));
        return products.stream().collect(Collectors.toMap(ProductInfo::id, Function.identity()));
    }

    public ProductInfo find(Long id) {
        ProductInfo product = findAll(List.of(id)).get(id);
        if (product == null) {
            throw new ResourceNotFoundException("Product", id);
        }
        return product;
    }

    // Removes the units from the stock, all lines or none: 404 unknown product, 409 not enough stock
    public List<ReservedItem> reserve(List<StockLine> lines) {
        return call(() -> restClient.post()
                .uri("/internal/stock/reservations")
                .body(Map.of("items", lines))
                .retrieve()
                .body(RESERVED));
    }

    // Gives the units back when the order could not be saved after the reservation
    public void release(List<StockLine> lines) {
        call(() -> restClient.post()
                .uri("/internal/stock/releases")
                .body(Map.of("items", lines))
                .retrieve()
                .toBodilessEntity());
    }

    // Connection refused, timeout, or no instance in Eureka (IllegalStateException of the load balancer)
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("product-service call failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    private ResponseStatusException toException(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.value() == HttpStatus.NOT_FOUND.value() || status.value() == HttpStatus.CONFLICT.value()) {
            return new ResponseStatusException(status, messageOf(response));
        }
        log.warn("product-service answered {}", status.value());
        return unavailable();
    }

    // The ErrorResponse of product-service: { "status", "error", "message", ... }
    private String messageOf(ClientHttpResponse response) {
        try {
            JsonNode body = objectMapper.readTree(response.getBody());
            return body.path("message").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable");
    }
}
