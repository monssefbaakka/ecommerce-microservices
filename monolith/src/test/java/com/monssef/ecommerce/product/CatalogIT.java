package com.monssef.ecommerce.product;

import com.monssef.ecommerce.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Categories and products: ADMIN writes, everyone reads, constraints checked by PostgreSQL
class CatalogIT extends IntegrationTest {

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
    }

    @Test
    void createdProduct_shouldBeReadableWithoutToken() throws Exception {
        long phones = createCategory(admin, "Phones");
        long id = createProduct(admin, phones, "iPhone 15", "999.99", 10);

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.categoryId").value(phones))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void search_shouldFilterByNameAndCategoryAndPage() throws Exception {
        long phones = createCategory(admin, "Phones");
        long laptops = createCategory(admin, "Laptops");
        createProduct(admin, phones, "iPhone 15", "999.99", 10);
        createProduct(admin, phones, "Galaxy S24", "899.99", 10);
        createProduct(admin, laptops, "MacBook Air", "1299.99", 10);
        createProduct(admin, phones, "iPhone 15 Case", "19.90", 10);

        mockMvc.perform(get("/api/products").param("name", "IPHONE").param("categoryId", String.valueOf(phones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("iPhone 15", "iPhone 15 Case")));

        mockMvc.perform(get("/api/products").param("size", "2").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("MacBook Air", "iPhone 15")))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void createCategory_shouldReturn409_whenNameTakenWithOtherCase() throws Exception {
        createCategory(admin, "Phones");

        mockMvc.perform(auth(json(post("/api/categories"), Map.of("name", "PHONES")), admin))
                .andExpect(status().isConflict());
    }

    @Test
    void createProduct_shouldReturn404_whenCategoryUnknown() throws Exception {
        Map<String, Object> request = Map.of("name", "iPhone 15", "price", "999.99", "categoryId", 99, "stock", 1);

        mockMvc.perform(auth(json(post("/api/products"), request), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_shouldMoveItToOtherCategory() throws Exception {
        long phones = createCategory(admin, "Phones");
        long accessories = createCategory(admin, "Accessories");
        long id = createProduct(admin, phones, "iPhone 15 Case", "19.90", 10);
        Map<String, Object> request = Map.of(
                "name", "iPhone 15 Case", "price", "24.90", "categoryId", accessories, "stock", 5);

        mockMvc.perform(auth(json(put("/api/products/{id}", id), request), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(accessories))
                .andExpect(jsonPath("$.price").value(24.90))
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void deleteCategory_shouldReturn409_whileProductsUseIt() throws Exception {
        long phones = createCategory(admin, "Phones");
        long id = createProduct(admin, phones, "iPhone 15", "999.99", 10);

        mockMvc.perform(auth(delete("/api/categories/{id}", phones), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category is still used by products"));

        mockMvc.perform(auth(delete("/api/products/{id}", id), admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(auth(delete("/api/categories/{id}", phones), admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/categories/{id}", phones))
                .andExpect(status().isNotFound());
    }

    @Test
    void writes_shouldReturn403_forUserRole() throws Exception {
        String user = registerAndLogin("john@example.com");

        mockMvc.perform(auth(json(post("/api/categories"), Map.of("name", "Phones")), user))
                .andExpect(status().isForbidden());
    }
}
