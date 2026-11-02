package com.monssef.productservice.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs tokens the way auth-service does (sub = email, role, iat, exp):
 * this service has no login endpoint, its tests write their own tokens.
 */
public final class TestTokens {

    // 32 bytes, Base64-encoded: the minimum for HS256
    public static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC0xMjM=";

    private TestTokens() {
    }

    public static String create(String secret, String email, String role, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }

    public static String create(String email, String role) {
        return create(SECRET, email, role, Duration.ofHours(1));
    }
}
