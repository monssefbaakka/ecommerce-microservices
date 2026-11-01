package com.monssef.authservice.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.authservice.user.Role;
import com.monssef.authservice.user.User;
import com.monssef.authservice.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class of the integration tests: the whole service (security, JPA, Hibernate DDL)
 * on a real PostgreSQL, called through MockMvc. No Eureka server in the tests: registration is off.
 * Skipped, not failed, when Docker is not running.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "secret123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    // Each test starts from an empty table: tests never depend on the order they run in
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE users RESTART IDENTITY");
    }

    // Through the API, like a client: the account always gets the USER role
    protected String registerAndLogin(String email) throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isCreated());
        return login(email);
    }

    // No endpoint creates an ADMIN: the account is written directly, then logs in through the API
    protected String adminToken() throws Exception {
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        return login(admin.getEmail());
    }

    protected String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + body(result).get("token").asText();
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
