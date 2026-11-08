package com.monssef.orderservice.product;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last answer of product-service for each product, kept in memory by this instance.
 * Filled by every successful read of ProductClient, read back only when product-service is
 * unavailable, to still show a cart (never to check the stock or to price an order).
 * Product data only, the same for every user: nothing personal is kept here.
 */
@Component
public class LastKnownProducts {

    private final Map<Long, ProductInfo> products = new ConcurrentHashMap<>();

    // asked: the ids sent to product-service. Those missing from the answer were deleted: forgotten
    public void remember(Collection<Long> asked, Collection<ProductInfo> answer) {
        asked.forEach(products::remove);
        answer.forEach(product -> products.put(product.id(), product));
    }

    // The products seen before among these ids, by id. A product never seen is missing from the map
    public Map<Long, ProductInfo> get(Collection<Long> ids) {
        Map<Long, ProductInfo> found = new HashMap<>();
        for (Long id : ids) {
            ProductInfo product = products.get(id);
            if (product != null) {
                found.put(id, product);
            }
        }
        return found;
    }
}
