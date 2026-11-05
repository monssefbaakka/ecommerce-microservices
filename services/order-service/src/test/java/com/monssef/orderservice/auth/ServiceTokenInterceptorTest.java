package com.monssef.orderservice.auth;

import com.monssef.orderservice.support.TestTokens;
import feign.RequestTemplate;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenInterceptorTest {

    private final ServiceTokenInterceptor interceptor =
            new ServiceTokenInterceptor(new ServiceTokenProvider(TestTokens.SECRET, Duration.ofMinutes(5)));

    @Test
    void apply_shouldAddABearerTokenWithTheServiceRole() {
        RequestTemplate request = new RequestTemplate();

        interceptor.apply(request);

        Collection<String> authorization = request.headers().get(HttpHeaders.AUTHORIZATION);
        assertThat(authorization).singleElement().asString().startsWith("Bearer ");
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestTokens.SECRET)))
                .build()
                .parseSignedClaims(authorization.iterator().next().substring("Bearer ".length()))
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("order-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void apply_shouldSetOneAuthorizationHeaderPerCall() {
        RequestTemplate first = new RequestTemplate();
        RequestTemplate second = new RequestTemplate();

        interceptor.apply(first);
        interceptor.apply(second);

        // One header per call, never piled up on a reused template
        assertThat(first.headers().get(HttpHeaders.AUTHORIZATION)).hasSize(1);
        assertThat(second.headers().get(HttpHeaders.AUTHORIZATION)).hasSize(1);
    }
}
