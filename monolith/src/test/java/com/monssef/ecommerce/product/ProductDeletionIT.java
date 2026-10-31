package com.monssef.ecommerce.product;

import com.monssef.ecommerce.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The foreign keys created by Hibernate: ON DELETE CASCADE on carts, ON DELETE SET NULL on orders
class ProductDeletionIT extends IntegrationTest {

    private String admin;
    private String user;
    private long iphone;
    private long caseId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        long phones = createCategory(admin, "Phones");
        iphone = createProduct(admin, phones, "iPhone 15", "999.99", 5);
        caseId = createProduct(admin, phones, "iPhone 15 Case", "19.90", 50);
        user = registerAndLogin("john@example.com");
    }

    @Test
    void deleteProduct_shouldRemoveItFromCarts() throws Exception {
        addToCart(user, iphone, 1);
        addToCart(user, caseId, 1);

        mockMvc.perform(auth(delete("/api/products/{id}", iphone), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/cart"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(caseId));
    }

    @Test
    void deleteProduct_shouldKeepPastOrderLines() throws Exception {
        addToCart(user, iphone, 2);
        MvcResult result = mockMvc.perform(auth(post("/api/orders"), user))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = body(result).get("id").asLong();

        mockMvc.perform(auth(delete("/api/products/{id}", iphone), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/orders/{id}", orderId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(1999.98));
    }
}
