package com.monssef.orderservice.auth;

import com.monssef.orderservice.support.TestTokens;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Plain unit test: the token is read back with the same shared key, like product-service does
class ServiceTokenProviderTest {

    private final ServiceTokenProvider provider = new ServiceTokenProvider(TestTokens.SECRET, Duration.ofMinutes(5));

    @Test
    void token_shouldCarryServiceRoleAndOrderServiceSubject() {
        Claims claims = parse(provider.token());

        assertThat(claims.getSubject()).isEqualTo("order-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void token_shouldBeShortLived() {
        Claims claims = parse(provider.token());

        assertThat(claims.getExpiration().toInstant())
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(5)), within(Duration.ofSeconds(5)));
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestTokens.SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
