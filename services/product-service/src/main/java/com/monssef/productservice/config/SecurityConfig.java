package com.monssef.productservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.productservice.auth.JwtAuthenticationFilter;
import com.monssef.productservice.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   ObjectMapper objectMapper) throws Exception {
        JsonSecurityErrorHandler errorHandler = new JsonSecurityErrorHandler(objectMapper);
        http
            .csrf(AbstractHttpConfigurer::disable) // no cookie session, so no CSRF risk
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Rules are checked in order: the first match wins
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll() // otherwise Spring Boot error pages turn into 401
                // Health checks come from Docker, Eureka or the gateway, without a token
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Calls from the other services (order-service), with a token they sign themselves (role SERVICE).
                // auth-service never gives this role to an account, and the gateway only routes /api/**
                .requestMatchers("/internal/**").hasRole("SERVICE")
                // Catalog: anyone can read, only ADMIN can write (same rules as the monolith)
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                .requestMatchers("/api/products/**", "/api/categories/**").hasRole("ADMIN")
                // Anything not listed above needs a token: nothing is open by mistake
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errorHandler) // 401
                .accessDeniedHandler(errorHandler))     // 403
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
