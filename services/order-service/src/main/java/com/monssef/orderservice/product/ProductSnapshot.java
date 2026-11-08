package com.monssef.orderservice.product;

import java.util.Map;

/**
 * Products by id, with where they come from.
 * upToDate: true when product-service just answered (a product missing from the map was deleted),
 * false when it was unavailable and these are the last known values (a missing product was never seen).
 */
public record ProductSnapshot(Map<Long, ProductInfo> products, boolean upToDate) {
}
