package com.monssef.authservice.auth;

import com.monssef.authservice.user.Role;
import com.monssef.authservice.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

    // The monolith checks the tokens on its own: it only knows the key and reads "sub" and "role"
    @Test
    void generateToken_shouldBeReadableByAnyServiceSharingTheKey() {
        String token = jwtService.generateToken(user("admin@example.com", Role.ADMIN));

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("admin@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
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
