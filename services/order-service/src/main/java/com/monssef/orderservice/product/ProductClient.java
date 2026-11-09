package com.monssef.orderservice.product;

import com.monssef.orderservice.client.RemoteCalls;
import com.monssef.orderservice.client.ServiceUnavailableException;
import com.monssef.orderservice.common.BusinessMetrics;
import com.monssef.orderservice.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * message (RemoteErrorDecoder); product-service down, not registered or failing gives a 503,
 * except for findAllOrLastKnown, which falls back on the last answer kept in LastKnownProducts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private final ProductServiceApi api;
    private final RemoteCalls calls;
    private final LastKnownProducts lastKnown;
    private final BusinessMetrics metrics;

    // Current name, price and stock, by id. A product deleted since is simply missing from the map
    public Map<Long, ProductInfo> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> answer = calls.read(() -> api.findAllById(ids));
        lastKnown.remember(ids, answer);
        return answer.stream().collect(Collectors.toMap(ProductInfo::id, Function.identity()));
    }

    /**
     * For display only: like findAll, but when product-service is unavailable (down, failing or its
     * circuit breaker open) the last known values instead of a 503, marked as not up to date.
     * Never for a decision: the stock and the prices of an order always come from product-service.
     */
    public ProductSnapshot findAllOrLastKnown(Collection<Long> ids) {
        try {
            return new ProductSnapshot(findAll(ids), true);
        } catch (ServiceUnavailableException e) {
            log.warn("Product service unavailable: last known products shown for {}", ids);
            metrics.cartShownFromLastKnownProducts();
            return new ProductSnapshot(lastKnown.get(ids), false);
        }
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
