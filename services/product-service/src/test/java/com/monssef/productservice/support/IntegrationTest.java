package com.monssef.productservice.support;

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

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class of the integration tests: the whole service (security, JPA, Hibernate DDL)
 * on a real PostgreSQL, called through MockMvc. No Eureka server in the tests: registration is off.
 * Tokens are signed with the service's own key, like auth-service would. Skipped, not failed, without Docker.
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
        jdbcTemplate.execute("TRUNCATE products, categories RESTART IDENTITY CASCADE");
    }

    protected String adminToken() {
        return "Bearer " + TestTokens.create(jwtSecret, "admin@example.com", "ADMIN", Duration.ofHours(1));
    }

    protected String userToken() {
        return "Bearer " + TestTokens.create(jwtSecret, "john@example.com", "USER", Duration.ofHours(1));
    }

    protected long createCategory(String adminToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(auth(json(post("/api/categories"), Map.of("name", name)), adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    protected long createProduct(String adminToken, long categoryId, String name, String price, int stock)
            throws Exception {
        Map<String, Object> request = Map.of("name", name, "price", price, "categoryId", categoryId, "stock", stock);
        MvcResult result = mockMvc.perform(auth(json(post("/api/products"), request), adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
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
