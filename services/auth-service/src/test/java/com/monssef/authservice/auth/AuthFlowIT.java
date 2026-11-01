package com.monssef.authservice.auth;

import com.monssef.authservice.support.IntegrationTest;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Register and log in on a real database
class AuthFlowIT extends IntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void register_shouldStoreHashedPasswordAndUserRole() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of("email", "John@Example.COM", "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT password, role FROM users WHERE email = ?",
                "john@example.com");
        assertThat((String) row.get("password")).startsWith("$2a$").isNotEqualTo(PASSWORD); // BCrypt
        assertThat(row.get("role")).isEqualTo("USER");
    }

    @Test
    void register_shouldReturn409_whenEmailTakenWithOtherCase() throws Exception {
        registerAndLogin("john@example.com");

        mockMvc.perform(json(post("/api/auth/register"), Map.of("email", "JOHN@example.com", "password", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void register_shouldReturn400_whenBodyInvalid() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of("email", "not-an-email", "password", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void login_shouldReturnTokenWithEmailAndRole() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of("email", "john@example.com", "password", PASSWORD)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("email", "john@example.com", "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();

        Claims claims = jwtService.parseClaims(body(result).get("token").asText());
        assertThat(claims.getSubject()).isEqualTo("john@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    // The users table is shared: an account created by the monolith logs in here with the same password
    @Test
    void login_shouldAcceptAnAccountWrittenByTheMonolith() throws Exception {
        jdbcTemplate.update("INSERT INTO users (email, password, role, created_at) VALUES (?, ?, 'USER', now())",
                "legacy@example.com", passwordEncoder.encode(PASSWORD));

        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", "legacy@example.com", "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_shouldReturn401_whenPasswordWrong() throws Exception {
        registerAndLogin("john@example.com");

        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", "john@example.com", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void unknownEndpoint_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/products"));
    }
}
