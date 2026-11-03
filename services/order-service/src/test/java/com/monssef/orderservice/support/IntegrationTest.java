package com.monssef.orderservice.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class of the integration tests: the whole service (security, JPA, Hibernate DDL)
 * on a real PostgreSQL, called through MockMvc. No Eureka server in the tests: registration is off.
 * The accounts and the products belong to auth-service and product-service: written here with SQL
 * in the shared tables, the way those services would. Skipped, not failed, without Docker.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // Each test starts from empty tables: tests never depend on the order they run in
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE order_items, orders, cart_items, carts, products, users "
                + "RESTART IDENTITY CASCADE");
    }

    // An account as auth-service creates it, and a token signed like auth-service does
    protected String userToken(String email) {
        jdbcTemplate.update("INSERT INTO users (id, email) VALUES ((SELECT coalesce(max(id), 0) + 1 FROM users), ?)",
                email);
        return tokenWithoutAccount(email, "USER");
    }

    // Valid signature, but no row in "users" (account deleted since the token was issued)
    protected String tokenWithoutAccount(String email, String role) {
        return "Bearer " + TestTokens.create(jwtSecret, email, role, Duration.ofHours(1));
    }

    protected String adminToken() {
        return tokenWithoutAccount("admin@example.com", "ADMIN");
    }

    // A product as product-service creates it: only the columns this service maps
    protected long createProduct(String name, String price, int stock) {
        return jdbcTemplate.queryForObject("INSERT INTO products (id, name, price, stock) "
                        + "VALUES ((SELECT coalesce(max(id), 0) + 1 FROM products), ?, ?, ?) RETURNING id",
                Long.class, name, new BigDecimal(price), stock);
    }

    // An ADMIN editing the product in product-service, between two requests of the user
    protected void updateProduct(long id, String name, String price, int stock) {
        jdbcTemplate.update("UPDATE products SET name = ?, price = ?, stock = ? WHERE id = ?",
                name, new BigDecimal(price), stock, id);
    }

    protected void addToCart(String token, long productId, int quantity) throws Exception {
        mockMvc.perform(auth(json(post("/api/cart/items"), Map.of("productId", productId, "quantity", quantity)), token))
                .andExpect(status().isOk());
    }

    protected long placeOrder(String token) throws Exception {
        MvcResult result = mockMvc.perform(auth(post("/api/orders"), token))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    protected int stockOf(long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, token);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
