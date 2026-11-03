package com.monssef.paymentservice.support;

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

/**
 * Base class of the integration tests: the whole service (security, JPA, Hibernate DDL)
 * on a real PostgreSQL, called through MockMvc. No Eureka server in the tests: registration is off.
 * The accounts and the orders belong to auth-service and order-service: written here with SQL
 * in the shared tables, the way those services would. Skipped, not failed, without Docker.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    protected static final String CARD = "4242424242424242";
    protected static final String DECLINED_CARD = "4000000000000002";

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
        jdbcTemplate.execute("TRUNCATE payments, orders, users RESTART IDENTITY CASCADE");
    }

    // An account as auth-service creates it, and a token signed like auth-service does
    protected String userToken(String email) {
        jdbcTemplate.update("INSERT INTO users (id, email) VALUES ((SELECT coalesce(max(id), 0) + 1 FROM users), ?)",
                email);
        return token(email, "USER");
    }

    protected String token(String email, String role) {
        return "Bearer " + TestTokens.create(jwtSecret, email, role, Duration.ofHours(1));
    }

    protected String adminToken() {
        return token("admin@example.com", "ADMIN");
    }

    // A PENDING order as order-service places it: only the columns this service maps
    protected long createOrder(String email, String total) {
        return jdbcTemplate.queryForObject("INSERT INTO orders (id, user_id, status, total) "
                        + "VALUES ((SELECT coalesce(max(id), 0) + 1 FROM orders), "
                        + "(SELECT id FROM users WHERE email = ?), 'PENDING', ?) RETURNING id",
                Long.class, email, new BigDecimal(total));
    }

    protected String statusOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    protected MockHttpServletRequestBuilder pay(String token, long orderId, String cardNumber) throws Exception {
        return auth(json(post("/api/payments"), Map.of("orderId", orderId, "cardNumber", cardNumber)), token);
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
