package com.monssef.orderservice.product;

import com.monssef.orderservice.client.ServiceUnavailableException;
import com.monssef.orderservice.common.ResourceNotFoundException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What the cart and the orders need from product-service, which owns the products and their stock.
 * The calls go through the ProductServiceApi Feign client, with a SERVICE token. A 404 or 409 of
 * product-service is passed on with its message (RemoteErrorDecoder); product-service down,
 * not registered or failing gives a 503.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private static final String SERVICE = "Product service";

    private final ProductServiceApi api;

    // Current name, price and stock, by id. A product deleted since is simply missing from the map
    public Map<Long, ProductInfo> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return call(() -> api.findAllById(ids)).stream()
                .collect(Collectors.toMap(ProductInfo::id, Function.identity()));
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
        return call(() -> api.reserve(new StockRequest(lines)));
    }

    // Gives the units back when the order could not be saved after the reservation
    public void release(List<StockLine> lines) {
        call(() -> {
            api.release(new StockRequest(lines));
            return null;
        });
    }

    // Connection refused, timeout or unreadable answer. The error answers are already turned
    // into a ResponseStatusException by the decoder, and go through untouched
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (FeignException e) {
            log.warn("product-service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException(SERVICE);
        }
    }
}
