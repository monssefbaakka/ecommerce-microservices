package com.monssef.productservice.auth;

import com.monssef.productservice.support.TestTokens;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain unit test: no Spring context
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(TestTokens.SECRET);

    @Test
    void parseClaims_shouldReadEmailAndRole_ofATokenSignedWithTheSharedKey() {
        String token = TestTokens.create("admin@example.com", "ADMIN");

        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("admin@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void parseClaims_shouldReject_whenSignedWithAnotherKey() {
        String token = TestTokens.create("b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci0xMjM=",
                "admin@example.com", "ADMIN", Duration.ofHours(1));

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaims_shouldReject_whenExpired() {
        String token = TestTokens.create(TestTokens.SECRET, "admin@example.com", "ADMIN", Duration.ofMinutes(-5));

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void constructor_shouldReject_aKeyShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtService("c2hvcnQta2V5")) // "short-key"
                .isInstanceOf(WeakKeyException.class);
    }
}
