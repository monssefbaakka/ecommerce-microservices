package com.monssef.ecommerce.auth;

import com.monssef.ecommerce.user.Role;
import com.monssef.ecommerce.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test: no Spring context, real JwtService with a test key
class JwtAuthenticationFilterTest {

    // 32 bytes, Base64-encoded: the minimum for HS256
    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC0xMjM=";

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(1));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticate_whenTokenIsValid() throws Exception {
        String token = jwtService.generateToken(user("admin@example.com", Role.ADMIN));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithHeader("Bearer " + token), new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("admin@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).isNotNull(); // the chain went on
    }

    @Test
    void shouldStayAnonymous_whenNoAuthorizationHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldStayAnonymous_whenHeaderIsNotBearer() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithHeader("Basic am9objpzZWNyZXQ="), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldStayAnonymous_whenTokenIsInvalid() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithHeader("Bearer not-a-jwt"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // no exception, the request goes on
    }

    @Test
    void shouldStayAnonymous_whenTokenIsExpired() throws Exception {
        JwtService expiredService = new JwtService(SECRET, Duration.ofSeconds(-1));
        String token = expiredService.generateToken(user("john@example.com", Role.USER));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithHeader("Bearer " + token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest requestWithHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, value);
        return request;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
