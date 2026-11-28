package com.monssef.orderservice.order;

import com.monssef.orderservice.client.ServiceUnavailableException;
import com.monssef.orderservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The expiry job on PostgreSQL: orders made old by moving created_at back, the job called by hand
// (it never fires by itself in the tests, see src/test/resources/config/application.yml)
class PendingOrderExpiryIT extends IntegrationTest {

    @Autowired
    private PendingOrderExpiry job;

    private String user;
    private long iphone;

    @BeforeEach
    void setUp() {
        iphone = createProduct("iPhone 15", "999.99", 10);
        user = userToken("john@example.com");
    }

    @Test
    void shouldCancelUnpaidOrdersOlderThanTheTimeoutAndGiveTheirStockBack() throws Exception {
        long old = orderOf(2, "1 hour");
        long recent = orderOf(1, "5 minutes");
        assertThat(stockOf(iphone)).isEqualTo(7);

        job.cancelExpiredOrders();

        assertThat(statusOf(old)).isEqualTo("CANCELLED");
        assertThat(statusOf(recent)).isEqualTo("PENDING"); // still time to pay it
        assertThat(stockOf(iphone)).isEqualTo(9);
    }

    @Test
    void shouldLeavePaidOrdersAlone() throws Exception {
        long paid = orderOf(2, "1 hour");
        mockMvc.perform(auth(post("/internal/orders/{id}/paid", paid), token("payment-service", "SERVICE")))
                .andExpect(status().isOk());

        job.cancelExpiredOrders();

        assertThat(statusOf(paid)).isEqualTo("PAID");
        assertThat(stockOf(iphone)).isEqualTo(8);
    }

    @Test
    void shouldKeepOrdersPendingAndTryAgain_whenProductServiceIsDown() throws Exception {
        long old = orderOf(2, "1 hour");
        doThrow(new ServiceUnavailableException("Product service")).when(productClient).release(anyList());

        job.cancelExpiredOrders();

        assertThat(statusOf(old)).isEqualTo("PENDING");
        assertThat(stockOf(iphone)).isEqualTo(8);

        reset(productClient); // product-service is back
        products.answerFor(productClient);
        job.cancelExpiredOrders();

        assertThat(statusOf(old)).isEqualTo("CANCELLED");
        assertThat(stockOf(iphone)).isEqualTo(10);
    }

    // An order of `quantity` iPhones, placed `age` ago (a PostgreSQL interval)
    private long orderOf(int quantity, String age) throws Exception {
        addToCart(user, iphone, quantity);
        long id = placeOrder(user);
        jdbcTemplate.update("UPDATE orders SET created_at = now() - ?::interval WHERE id = ?", age, id);
        return id;
    }

    private String statusOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }
}
