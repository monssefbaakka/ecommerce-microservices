package com.monssef.orderservice.product;

import com.monssef.orderservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A product deleted in product-service: no foreign key to cascade any more, order-service finds out on the next call
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
    void deletedProduct_shouldLeaveTheCart() throws Exception {
        addToCart(user, iphone, 1);
        addToCart(user, caseId, 1);

        products.delete(iphone);

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(caseId))
                .andExpect(jsonPath("$.total").value(19.90));
    }

    @Test
    void placeOrder_shouldOrderOnlyTheProductsLeft() throws Exception {
        addToCart(user, iphone, 1);
        addToCart(user, caseId, 2);

        products.delete(iphone);

        mockMvc.perform(auth(post("/api/orders"), user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(caseId))
                .andExpect(jsonPath("$.total").value(39.80));
        assertThat(stockOf(caseId)).isEqualTo(48);
    }

    @Test
    void placeOrder_shouldReturn400_whenEveryProductOfTheCartWasDeleted() throws Exception {
        addToCart(user, iphone, 1);

        products.delete(iphone);

        mockMvc.perform(auth(post("/api/orders"), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    void deletedProduct_shouldKeepPastOrderLinesAsTheyWere() throws Exception {
        addToCart(user, iphone, 2);
        long orderId = placeOrder(user);

        products.delete(iphone);

        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(iphone))
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(1999.98));
    }
}
