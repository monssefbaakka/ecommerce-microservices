package com.monssef.productservice.stock;

import com.monssef.productservice.product.InsufficientStockException;
import com.monssef.productservice.stock.dto.StockLine;
import com.monssef.productservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The stock owned by product-service, changed by order-service through /internal/stock
class StockIT extends IntegrationTest {

    @Autowired
    private StockService stockService;

    private long iphone;
    private long caseId;

    @BeforeEach
    void setUp() throws Exception {
        String admin = adminToken();
        long phones = createCategory(admin, "Phones");
        iphone = createProduct(admin, phones, "iPhone 15", "999.99", 5);
        caseId = createProduct(admin, phones, "iPhone 15 Case", "19.90", 50);
    }

    @Test
    void reserve_shouldRemoveStockAndReturnNameAndPrice() throws Exception {
        mockMvc.perform(auth(json(post("/internal/stock/reservations"), reservation(iphone, 2, caseId, 1)), serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("iPhone 15"))
                .andExpect(jsonPath("$[0].price").value(999.99))
                .andExpect(jsonPath("$[1].quantity").value(1));

        assertThat(stockOf(iphone)).isEqualTo(3);
        assertThat(stockOf(caseId)).isEqualTo(49);
    }

    @Test
    void reserve_shouldChangeNothing_whenOneProductLacksStock() throws Exception {
        mockMvc.perform(auth(json(post("/internal/stock/reservations"), reservation(caseId, 1, iphone, 6)), serviceToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock for product %d (iPhone 15): 5 left"
                        .formatted(iphone)));

        assertThat(stockOf(caseId)).isEqualTo(50);
        assertThat(stockOf(iphone)).isEqualTo(5);
    }

    @Test
    void release_shouldGiveTheUnitsBack() throws Exception {
        mockMvc.perform(auth(json(post("/internal/stock/reservations"), reservation(iphone, 2, caseId, 1)), serviceToken()))
                .andExpect(status().isOk());

        mockMvc.perform(auth(json(post("/internal/stock/releases"), reservation(iphone, 2, caseId, 1)), serviceToken()))
                .andExpect(status().isNoContent());

        assertThat(stockOf(iphone)).isEqualTo(5);
        assertThat(stockOf(caseId)).isEqualTo(50);
    }

    @Test
    void reserve_shouldBeForbidden_withAUserToken() throws Exception {
        mockMvc.perform(auth(json(post("/internal/stock/reservations"), reservation(iphone, 5, caseId, 1)), userToken()))
                .andExpect(status().isForbidden());

        assertThat(stockOf(iphone)).isEqualTo(5);
    }

    @Test
    void findProducts_shouldReturnTheKnownOnes() throws Exception {
        mockMvc.perform(auth(get("/internal/products").param("ids", iphone + "," + 999), serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stock").value(5));
    }

    @Test
    void lastUnit_shouldBeReservedOnlyOnce() throws Exception {
        String admin = adminToken();
        long last = createProduct(admin, createCategory(admin, "Rare"), "Last one", "10.00", 1);
        int buyers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return stockService.reserve(List.of(new StockLine(last, 1)));
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                fail("reservation threads did not stop");
            }
        }

        assertThat(failures).hasSize(buyers - 1).allMatch(InsufficientStockException.class::isInstance);
        assertThat(stockOf(last)).isZero();
    }

    private Map<String, Object> reservation(long firstId, int firstQuantity, long secondId, int secondQuantity) {
        return Map.of("items", List.of(
                Map.of("productId", firstId, "quantity", firstQuantity),
                Map.of("productId", secondId, "quantity", secondQuantity)));
    }

    private int stockOf(long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }
}
