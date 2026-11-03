package com.monssef.orderservice.support;

import com.monssef.orderservice.common.ResourceNotFoundException;
import com.monssef.orderservice.product.ProductClient;
import com.monssef.orderservice.product.ProductInfo;
import com.monssef.orderservice.product.ReservedItem;
import com.monssef.orderservice.product.StockLine;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * product-service in memory, behind the mocked ProductClient of the integration tests:
 * the same answers (404, 409, all-or-nothing reservation) without Eureka or a second service.
 * The real reservation, with row locks on PostgreSQL, is tested in product-service (StockIT).
 * Synchronized: the concurrency tests reserve from several threads at once.
 */
public class FakeProductService {

    private final Map<Long, ProductInfo> products = new HashMap<>();
    private long nextId = 1;

    public synchronized long create(String name, String price, int stock) {
        long id = nextId++;
        products.put(id, new ProductInfo(id, name, new BigDecimal(price), stock));
        return id;
    }

    // An ADMIN editing the product in product-service, between two requests of the user
    public synchronized void update(long id, String name, String price, int stock) {
        products.put(id, new ProductInfo(id, name, new BigDecimal(price), stock));
    }

    public synchronized void delete(long id) {
        products.remove(id);
    }

    public synchronized int stockOf(long id) {
        return products.get(id).stock();
    }

    public void answerFor(ProductClient client) {
        when(client.findAll(anyCollection())).thenAnswer(call -> findAll(call.getArgument(0)));
        when(client.find(anyLong())).thenAnswer(call -> find(call.getArgument(0)));
        when(client.reserve(anyList())).thenAnswer(call -> reserve(call.getArgument(0)));
        doAnswer(call -> {
            release(call.getArgument(0));
            return null;
        }).when(client).release(any());
    }

    private synchronized Map<Long, ProductInfo> findAll(Collection<Long> ids) {
        Map<Long, ProductInfo> found = new HashMap<>();
        ids.stream().filter(products::containsKey).forEach(id -> found.put(id, products.get(id)));
        return found;
    }

    private synchronized ProductInfo find(Long id) {
        ProductInfo product = products.get(id);
        if (product == null) {
            throw new ResourceNotFoundException("Product", id);
        }
        return product;
    }

    private synchronized List<ReservedItem> reserve(List<StockLine> lines) {
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        lines.forEach(line -> wanted.merge(line.productId(), line.quantity(), Integer::sum));
        wanted.forEach((id, quantity) -> {
            ProductInfo product = products.get(id);
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product with id %d not found".formatted(id));
            }
            if (!product.hasStock(quantity)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for product %d (%s): %d left"
                        .formatted(id, product.name(), product.stock()));
            }
        });
        wanted.forEach((id, quantity) -> changeStock(id, -quantity));
        return lines.stream().map(line -> {
            ProductInfo product = products.get(line.productId());
            return new ReservedItem(product.id(), product.name(), product.price(), line.quantity());
        }).toList();
    }

    private synchronized void release(List<StockLine> lines) {
        lines.stream()
                .filter(line -> products.containsKey(line.productId()))
                .forEach(line -> changeStock(line.productId(), line.quantity()));
    }

    private void changeStock(long id, int delta) {
        ProductInfo product = products.get(id);
        products.put(id, new ProductInfo(id, product.name(), product.price(), product.stock() + delta));
    }
}
