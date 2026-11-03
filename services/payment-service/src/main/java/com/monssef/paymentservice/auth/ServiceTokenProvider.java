package com.monssef.paymentservice.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs the token payment-service sends to the /internal endpoints of the other services:
 * same key and claims as auth-service (sub, role), with the role SERVICE that no account ever gets.
 * Short-lived, and never given to a client.
 */
@Component
public class ServiceTokenProvider {

    public static final String SERVICE_ROLE = "SERVICE";
    private static final String SUBJECT = "payment-service";

    private final SecretKey key;
    private final Duration validity;

    public ServiceTokenProvider(@Value("${jwt.secret}") String secret,
                                @Value("${jwt.service-token-validity:5m}") Duration validity) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.validity = validity;
    }

    public String token() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SUBJECT)
                .claim("role", SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }
}
