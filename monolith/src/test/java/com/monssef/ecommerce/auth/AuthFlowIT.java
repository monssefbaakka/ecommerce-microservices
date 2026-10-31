package com.monssef.ecommerce.auth;

import com.monssef.ecommerce.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Register, log in and use the token, on a real database
class AuthFlowIT extends IntegrationTest {

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
    void login_shouldReturnTokenUsableOnProtectedEndpoint() throws Exception {
        String token = registerAndLogin("john@example.com");

        mockMvc.perform(auth(get("/api/cart"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void login_shouldReturn401_whenPasswordWrong() throws Exception {
        registerAndLogin("john@example.com");

        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", "john@example.com", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpoint_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/cart"));
    }
}
