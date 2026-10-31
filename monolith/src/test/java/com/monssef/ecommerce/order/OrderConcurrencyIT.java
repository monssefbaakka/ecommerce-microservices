package com.monssef.ecommerce.order;

import com.monssef.ecommerce.product.InsufficientStockException;
import com.monssef.ecommerce.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Orders placed at the same time, each in its own thread and transaction.
 * Only the row locks of OrderService (SELECT ... FOR UPDATE) keep the stock right here:
 * without them both orders would read the same stock and both succeed.
 */
class OrderConcurrencyIT extends IntegrationTest {

    private static final int BUYERS = 5;

    @Autowired
    private OrderService orderService;

    @Test
    void lastUnit_shouldBeSoldOnlyOnce() throws Exception {
        String admin = adminToken();
        long iphone = createProduct(admin, createCategory(admin, "Phones"), "iPhone 15", "999.99", 1);
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            String email = "buyer" + i + "@example.com";
            addToCart(registerAndLogin(email), iphone, 1); // every cart wants the last unit
            emails.add(email);
        }

        List<Throwable> failures = placeAtTheSameTime(emails);

        assertThat(failures).hasSize(BUYERS - 1).allMatch(InsufficientStockException.class::isInstance);
        assertThat(stockOf(iphone)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameCart_shouldBeOrderedOnlyOnce() throws Exception {
        String admin = adminToken();
        long iphone = createProduct(admin, createCategory(admin, "Phones"), "iPhone 15", "999.99", 10);
        addToCart(registerAndLogin("john@example.com"), iphone, 2);

        // a double click on "order": the second request waits for the cart lock, then finds it empty
        List<Throwable> failures = placeAtTheSameTime(List.of("john@example.com", "john@example.com"));

        assertThat(failures).singleElement()
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(stockOf(iphone)).isEqualTo(8);
    }

    // Starts every order together and returns the exceptions of the ones that failed
    private List<Throwable> placeAtTheSameTime(List<String> emails) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(emails.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String email : emails) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return orderService.placeOrder(email);
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                fail("order threads did not stop");
            }
        }
    }
}
