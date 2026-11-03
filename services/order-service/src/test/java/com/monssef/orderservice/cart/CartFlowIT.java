package com.monssef.orderservice.cart;

import com.monssef.orderservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The cart of the logged-in user, saved in PostgreSQL between requests
class CartFlowIT extends IntegrationTest {

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
    void addItem_shouldCreateCartAndSumQuantities() throws Exception {
        addToCart(user, iphone, 1);
        addToCart(user, caseId, 2);
        addToCart(user, iphone, 1); // same product again: one line, quantity 2

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(iphone))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.totalQuantity").value(4))
                .andExpect(jsonPath("$.total").value(2039.78));
    }

    @Test
    void addItem_shouldReturn409_whenTotalQuantityAboveStock() throws Exception {
        addToCart(user, iphone, 4);

        mockMvc.perform(auth(json(post("/api/cart/items"), Map.of("productId", iphone, "quantity", 2)), user))
                .andExpect(status().isConflict());

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(jsonPath("$.items[0].quantity").value(4));
    }

    @Test
    void addItem_shouldReturn404_whenProductUnknown() throws Exception {
        mockMvc.perform(auth(json(post("/api/cart/items"), Map.of("productId", 99, "quantity", 1)), user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product with id 99 not found"));
    }

    @Test
    void addItem_shouldReturn401_whenAccountNoLongerExists() throws Exception {
        String deleted = tokenWithoutAccount("ghost@example.com", "USER");

        mockMvc.perform(auth(json(post("/api/cart/items"), Map.of("productId", iphone, "quantity", 1)), deleted))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void cart_shouldShowCurrentPrice_whenProductChangesInProductService() throws Exception {
        addToCart(user, iphone, 2);

        updateProduct(iphone, "iPhone 15", "899.99", 5);

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(jsonPath("$.items[0].unitPrice").value(899.99))
                .andExpect(jsonPath("$.total").value(1799.98));
    }

    @Test
    void updateAndRemoveItem_shouldChangeTheStoredCart() throws Exception {
        addToCart(user, iphone, 1);
        addToCart(user, caseId, 1);

        mockMvc.perform(auth(json(put("/api/cart/items/{id}", caseId), Map.of("quantity", 3)), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].quantity").value(3));
        mockMvc.perform(auth(delete("/api/cart/items/{id}", iphone), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(jsonPath("$.items[0].productId").value(caseId))
                .andExpect(jsonPath("$.total").value(59.70));
    }

    @Test
    void clear_shouldEmptyCart() throws Exception {
        addToCart(user, iphone, 1);

        mockMvc.perform(auth(delete("/api/cart"), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void cart_shouldBeSeparatePerUser() throws Exception {
        addToCart(user, iphone, 1);
        String other = userToken("jane@example.com");

        mockMvc.perform(auth(get("/api/cart"), other))
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(auth(delete("/api/cart/items/{id}", iphone), other))
                .andExpect(status().isNotFound());
    }
}
