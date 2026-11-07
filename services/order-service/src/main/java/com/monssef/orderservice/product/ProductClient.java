package com.monssef.orderservice.product;

import com.monssef.orderservice.client.RemoteCalls;
import com.monssef.orderservice.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the cart and the orders need from product-service, which owns the products and their stock.
 * The calls go through the ProductServiceApi Feign client, with a SERVICE token, and through
 * RemoteCalls: circuit breaker and retries. A 404 or 409 of product-service is passed on with its
 * message (RemoteErrorDecoder); product-service down, not registered or failing gives a 503.
 */
@Component
@RequiredArgsConstructor
public class ProductClient {

    private final ProductServiceApi api;
    private final RemoteCalls calls;

    // Current name, price and stock, by id. A product deleted since is simply missing from the map
    public Map<Long, ProductInfo> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return calls.read(() -> api.findAllById(ids)).stream()
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
        return calls.write(() -> api.reserve(new StockRequest(lines)));
    }

    // Gives the units back when the order could not be saved after the reservation
    public void release(List<StockLine> lines) {
        calls.write(() -> {
            api.release(new StockRequest(lines));
            return null;
        });
    }
}
