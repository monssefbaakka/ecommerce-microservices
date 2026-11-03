package com.monssef.orderservice.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.orderservice.product.ProductClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class of the integration tests: the whole service (security, JPA, Hibernate DDL)
 * on a real PostgreSQL, called through MockMvc. No Eureka server in the tests: registration is off.
 * product-service is not started: the ProductClient is a mock answered by an in-memory
 * FakeProductService. The accounts live in auth-service: a signed token is all this service sees.
 * Skipped, not failed, without Docker.
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

    @MockBean
    protected ProductClient productClient;

    protected FakeProductService products;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // Each test starts from empty tables and an empty catalog: tests never depend on the order they run in
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE order_items, orders, cart_items, carts RESTART IDENTITY CASCADE");
        products = new FakeProductService();
        products.answerFor(productClient); // the mock is reset after each test
    }

    // A token signed like auth-service does
    protected String userToken(String email) {
        return token(email, "USER");
    }

    protected String token(String email, String role) {
        return "Bearer " + TestTokens.create(jwtSecret, email, role, Duration.ofHours(1));
    }

    protected String adminToken() {
        return token("admin@example.com", "ADMIN");
    }

    protected long createProduct(String name, String price, int stock) {
        return products.create(name, price, stock);
    }

    protected void updateProduct(long id, String name, String price, int stock) {
        products.update(id, name, price, stock);
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
        return products.stockOf(productId);
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
