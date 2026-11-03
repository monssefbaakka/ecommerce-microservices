package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.payment.dto.PaymentRequest;
import com.monssef.paymentservice.support.IntegrationTest;
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
 * Payments of the same order at the same time, each in its own thread and transaction.
 * Only the row lock of PaymentService (SELECT ... FOR UPDATE) prevents a double charge here:
 * without it every payment would read the order as PENDING and succeed.
 */
class PaymentConcurrencyIT extends IntegrationTest {

    private static final int CLICKS = 5;

    @Autowired
    private PaymentService paymentService;

    @Test
    void sameOrder_shouldBeChargedOnlyOnce() throws Exception {
        userToken("john@example.com");
        long orderId = createOrder("john@example.com", "1999.98");

        List<Throwable> failures = payAtTheSameTime(orderId, CLICKS);

        assertThat(failures).hasSize(CLICKS - 1)
                .allSatisfy(ex -> assertThat(ex).isInstanceOf(ResponseStatusException.class)
                        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(statusOf(orderId)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
    }

    // Starts every payment together and returns the exceptions of the ones that failed
    private List<Throwable> payAtTheSameTime(long orderId, int count) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return paymentService.pay("john@example.com", new PaymentRequest(orderId, CARD));
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
                fail("payment threads did not stop");
            }
        }
    }
}
