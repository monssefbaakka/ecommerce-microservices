package com.monssef.productservice.stock;

import com.monssef.productservice.common.ResourceNotFoundException;
import com.monssef.productservice.product.Product;
import com.monssef.productservice.product.ProductRepository;
import com.monssef.productservice.stock.dto.ReservedItem;
import com.monssef.productservice.stock.dto.StockLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The stock of the products, changed only by order-service when an order is placed.
 * All the lines of an order are reserved in one transaction: if one product is unknown (404)
 * or lacks stock (409), no stock changes at all.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;

    // The rows stay locked until the commit: two orders of the same product run one after the other,
    // and the second one reads the stock left by the first, so the last unit is sold only once
    @Transactional
    public List<ReservedItem> reserve(List<StockLine> lines) {
        Map<Long, Integer> quantities = sumByProduct(lines);
        Map<Long, Product> products = lockProducts(quantities);
        quantities.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("Product", productId);
            }
            product.removeStock(quantity); // 409 rolls back the lines already removed; saved by dirty checking
        });
        return quantities.entrySet().stream()
                .map(line -> {
                    Product product = products.get(line.getKey());
                    return new ReservedItem(product.getId(), product.getName(), product.getPrice(), line.getValue());
                })
                .toList();
    }

    // Undoes a reservation when the order could not be saved. A product deleted since is skipped
    @Transactional
    public void release(List<StockLine> lines) {
        Map<Long, Integer> quantities = sumByProduct(lines);
        Map<Long, Product> products = lockProducts(quantities);
        quantities.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product != null) {
                product.addStock(quantity);
            }
        });
    }

    // Same product twice in the request: one line with the total, in the order of the request
    private Map<Long, Integer> sumByProduct(List<StockLine> lines) {
        return lines.stream().collect(Collectors.toMap(
                StockLine::getProductId, StockLine::getQuantity, Integer::sum, LinkedHashMap::new));
    }

    private Map<Long, Product> lockProducts(Map<Long, Integer> quantities) {
        return productRepository.findAllForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
