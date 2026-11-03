package com.monssef.paymentservice.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.paymentservice.order.OrderClient;
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

/**
 * Base class of the integration tests: the whole service (security, JPA, Hibernate DDL)
 * on a real PostgreSQL, called through MockMvc. No Eureka server in the tests: registration is off.
 * order-service is not started: the OrderClient is a mock answered by an in-memory FakeOrderService.
 * The accounts live in auth-service: a signed token is all this service sees.
 * Skipped, not failed, without Docker.
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

    @MockBean
    protected OrderClient orderClient;

    protected FakeOrderService orders;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // Each test starts from an empty table and no orders: tests never depend on the order they run in
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE payments RESTART IDENTITY");
        orders = new FakeOrderService();
        orders.answerFor(orderClient); // the mock is reset after each test
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

    protected long createOrder(String email, String total) {
        return orders.create(email, total);
    }

    protected String statusOf(long orderId) {
        return orders.statusOf(orderId);
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
