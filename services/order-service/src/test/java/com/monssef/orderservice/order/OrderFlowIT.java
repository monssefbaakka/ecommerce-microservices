package com.monssef.orderservice.order;

import com.monssef.orderservice.client.ServiceUnavailableException;
import com.monssef.orderservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cart -> order: stock reserved in product-service (in memory here), cart emptied and prices copied in the database
class OrderFlowIT extends IntegrationTest {

    private String user;
    private long iphone;
    private long caseId;

    @BeforeEach
    void setUp() {
        iphone = createProduct("iPhone 15", "999.99", 5);
        caseId = createProduct("iPhone 15 Case", "19.90", 50);
        user = userToken("john@example.com");
    }

    @Test
    void placeOrder_shouldRemoveStockAndEmptyCart() throws Exception {
        addToCart(user, iphone, 2);
        addToCart(user, caseId, 1);

        MvcResult result = mockMvc.perform(auth(post("/api/orders"), user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.total").value(2019.88))
                .andReturn();
        long orderId = body(result).get("id").asLong();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/orders/" + orderId);

        // taken in product-service
        assertThat(stockOf(iphone)).isEqualTo(3);
        assertThat(stockOf(caseId)).isEqualTo(49);
        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void placeOrder_shouldReturn400_whenCartEmpty() throws Exception {
        mockMvc.perform(auth(post("/api/orders"), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    void placeOrder_shouldChangeNothing_whenOneProductLacksStock() throws Exception {
        addToCart(user, caseId, 2);
        addToCart(user, iphone, 5);
        // the stock drops after the product was added to the cart
        updateProduct(iphone, "iPhone 15", "999.99", 1);

        mockMvc.perform(auth(post("/api/orders"), user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock for product %d (iPhone 15): 1 left"
                        .formatted(iphone)));

        // all or nothing: the case stock is untouched, the cart is still full, no order row
        assertThat(stockOf(caseId)).isEqualTo(50);
        assertThat(stockOf(iphone)).isEqualTo(1);
        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(jsonPath("$.items.length()").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void order_shouldKeepPriceOfPurchaseTime() throws Exception {
        addToCart(user, iphone, 1);
        long orderId = placeOrder(user);
        updateProduct(iphone, "iPhone 15 Pro", "1299.99", 10);

        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(999.99))
                .andExpect(jsonPath("$.total").value(999.99));
    }

    @Test
    void placeOrder_shouldReturn503AndKeepTheCart_whenProductServiceIsDown() throws Exception {
        addToCart(user, iphone, 1);
        when(productClient.reserve(anyList()))
                .thenThrow(new ServiceUnavailableException("Product service"));

        mockMvc.perform(auth(post("/api/orders"), user))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Product service unavailable"));

        assertThat(stockOf(iphone)).isEqualTo(5);
        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(jsonPath("$.items.length()").value(1));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void order_shouldBePaidOnlyOnce_throughTheInternalEndpoint() throws Exception {
        addToCart(user, iphone, 1);
        long orderId = placeOrder(user);
        String paymentService = token("payment-service", "SERVICE");

        // what payment-service calls once the card is accepted
        mockMvc.perform(auth(get("/internal/orders/{id}", orderId), paymentService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("john@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(999.99));
        mockMvc.perform(auth(post("/internal/orders/{id}/paid", orderId), paymentService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(auth(post("/internal/orders/{id}/paid", orderId), paymentService))
                .andExpect(status().isConflict());
        // the user cannot mark their order as paid themselves
        mockMvc.perform(auth(post("/internal/orders/{id}/paid", orderId), user))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_shouldGiveTheStockBackAndRefuseAPaymentAfterwards() throws Exception {
        addToCart(user, iphone, 2);
        addToCart(user, caseId, 1);
        long orderId = placeOrder(user);
        assertThat(stockOf(iphone)).isEqualTo(3);

        mockMvc.perform(auth(post("/api/orders/{id}/cancel", orderId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.total").value(2019.88)); // kept in the history as it was

        assertThat(stockOf(iphone)).isEqualTo(5);
        assertThat(stockOf(caseId)).isEqualTo(50);
        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // a second cancellation never gives the stock back twice
        mockMvc.perform(auth(post("/api/orders/{id}/cancel", orderId), user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Order %d is CANCELLED, only a PENDING order can be cancelled".formatted(orderId)));
        assertThat(stockOf(iphone)).isEqualTo(5);
        // payment-service is refused too
        mockMvc.perform(auth(post("/internal/orders/{id}/paid", orderId), token("payment-service", "SERVICE")))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_shouldReturn409_whenTheOrderIsPaid() throws Exception {
        addToCart(user, iphone, 1);
        long orderId = placeOrder(user);
        mockMvc.perform(auth(post("/internal/orders/{id}/paid", orderId), token("payment-service", "SERVICE")))
                .andExpect(status().isOk());

        mockMvc.perform(auth(post("/api/orders/{id}/cancel", orderId), user))
                .andExpect(status().isConflict());

        assertThat(stockOf(iphone)).isEqualTo(4); // a paid order keeps its units
    }

    @Test
    void cancel_shouldReturn404_forSomeoneElsesOrder() throws Exception {
        addToCart(user, iphone, 1);
        long orderId = placeOrder(user);

        mockMvc.perform(auth(post("/api/orders/{id}/cancel", orderId), userToken("jane@example.com")))
                .andExpect(status().isNotFound());

        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(stockOf(iphone)).isEqualTo(4);
    }

    @Test
    void cancel_shouldLeaveTheOrderPending_whenProductServiceIsDown() throws Exception {
        addToCart(user, iphone, 1);
        long orderId = placeOrder(user);
        doThrow(new ServiceUnavailableException("Product service")).when(productClient).release(anyList());

        mockMvc.perform(auth(post("/api/orders/{id}/cancel", orderId), user))
                .andExpect(status().isServiceUnavailable());

        // the status written before the call is rolled back: still PENDING, and its stock still taken
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PENDING");
        assertThat(stockOf(iphone)).isEqualTo(4);
    }

    @Test
    void history_shouldListOnlyOwnOrdersNewestFirst() throws Exception {
        addToCart(user, iphone, 1);
        long first = placeOrder(user);
        addToCart(user, caseId, 1);
        long second = placeOrder(user);
        String other = userToken("jane@example.com");
        addToCart(other, caseId, 1);
        long otherOrder = placeOrder(other);

        mockMvc.perform(auth(get("/api/orders"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // someone else's order looks like an unknown one
        mockMvc.perform(auth(get("/api/orders/{id}", otherOrder), user))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/api/orders/{id}", otherOrder), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otherOrder));
    }
}
