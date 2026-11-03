package com.monssef.orderservice.auth;

import com.monssef.orderservice.support.TestTokens;
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
        String token = TestTokens.create("john@example.com", "USER");

        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("john@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void parseClaims_shouldReject_whenSignedWithAnotherKey() {
        String token = TestTokens.create("b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci0xMjM=",
                "john@example.com", "USER", Duration.ofHours(1));

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaims_shouldReject_whenExpired() {
        String token = TestTokens.create(TestTokens.SECRET, "john@example.com", "USER", Duration.ofMinutes(-5));

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void constructor_shouldReject_aKeyShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtService("c2hvcnQta2V5")) // "short-key"
                .isInstanceOf(WeakKeyException.class);
    }
}
