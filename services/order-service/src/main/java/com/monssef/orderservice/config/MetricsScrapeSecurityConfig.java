package com.monssef.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

/**
 * /actuator/prometheus, read every few seconds by the Prometheus container. Prometheus cannot log in to
 * auth-service and renew a JWT: it sends a fixed account with HTTP Basic (metrics.scraper in
 * application.yml), the usual way to protect a scrape endpoint.
 * <p>
 * A chain of its own, checked before the JWT chain of {@link SecurityConfig} and limited to this one
 * path: the scraper account is known nowhere else, so it can read the metrics and nothing more, and a
 * user token is never accepted here.
 */
@Configuration
public class MetricsScrapeSecurityConfig {

    static final String ROLE = "METRICS";

    @Bean
    @Order(1)
    public SecurityFilterChain metricsScrapeChain(HttpSecurity http, ObjectMapper objectMapper,
                                                  @Value("${metrics.scraper.username}") String username,
                                                  @Value("${metrics.scraper.password}") String password) throws Exception {
        JsonSecurityErrorHandler errorHandler = new JsonSecurityErrorHandler(objectMapper);
        http
            .securityMatcher("/actuator/prometheus")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(ROLE))
            // Same JSON 401 as the rest of the service when the account is missing or wrong
            .httpBasic(basic -> basic.authenticationEntryPoint(errorHandler))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
            // Its own account list, never shared with the rest of the service
            .authenticationManager(scraperOnly(username, password));
        return http.build();
    }

    private static ProviderManager scraperOnly(String username, String password) {
        // Hashed once at startup: the plain password is not kept in memory next to the user list
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password))
                .roles(ROLE)
                .build());
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
