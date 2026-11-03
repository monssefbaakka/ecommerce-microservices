package com.monssef.orderservice.product;

import com.monssef.orderservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A product deleted by product-service in the shared table: ON DELETE CASCADE on carts, SET NULL on orders
class ProductDeletionIT extends IntegrationTest {

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
    void deletedProduct_shouldLeaveTheCarts() throws Exception {
        addToCart(user, iphone, 1);
        addToCart(user, caseId, 1);

        jdbcTemplate.update("DELETE FROM products WHERE id = ?", iphone);

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(caseId));
    }

    @Test
    void deletedProduct_shouldKeepPastOrderLines() throws Exception {
        addToCart(user, iphone, 2);
        long orderId = placeOrder(user);

        jdbcTemplate.update("DELETE FROM products WHERE id = ?", iphone);

        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(1999.98));
    }
}
