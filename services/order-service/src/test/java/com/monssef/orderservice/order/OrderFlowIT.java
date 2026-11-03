package com.monssef.orderservice.order;

import com.monssef.orderservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cart -> order in one transaction: stock, cart and price snapshot checked in the database
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

        // the shared products table: product-service sees the new stock at once
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

        // rolled back: the case stock is untouched, the cart is still full, no order row
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
    void order_shouldShowTheStatusWrittenByPaymentService() throws Exception {
        addToCart(user, iphone, 1);
        long orderId = placeOrder(user);

        // what payment-service does once the payment succeeds (same table)
        jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId);

        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(jsonPath("$.status").value("PAID"));
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
