package com.monssef.orderservice.auth;

import com.monssef.orderservice.support.TestTokens;
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

// Plain unit test: no Spring context, real JwtService with a test key and tokens signed like auth-service
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = new JwtService(TestTokens.SECRET);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticate_whenTokenIsValid() throws Exception {
        String token = TestTokens.create("john@example.com", "USER");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithHeader("Bearer " + token), new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("john@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
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
        String token = TestTokens.create(TestTokens.SECRET, "john@example.com", "USER", Duration.ofSeconds(-1));
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
}
