package com.monssef.ecommerce.auth;

import com.monssef.ecommerce.user.Role;
import com.monssef.ecommerce.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain unit test: no Spring context
class JwtServiceTest {

    // 32 bytes, Base64-encoded: the minimum for HS256
    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC0xMjM=";

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(1));

    @Test
    void generateToken_shouldContainEmailRoleAndExpiration() {
        String token = jwtService.generateToken(user("john@example.com", Role.USER));

        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("john@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(Duration.ofHours(1).toMillis());
    }

    @Test
    void parseClaims_shouldReject_whenSignedWithAnotherKey() {
        JwtService otherService = new JwtService(
                "b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci0xMjM=", Duration.ofHours(1));
        String token = otherService.generateToken(user("john@example.com", Role.USER));

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaims_shouldReject_whenExpired() {
        JwtService expiredService = new JwtService(SECRET, Duration.ofMinutes(-5));
        String token = expiredService.generateToken(user("john@example.com", Role.USER));

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
