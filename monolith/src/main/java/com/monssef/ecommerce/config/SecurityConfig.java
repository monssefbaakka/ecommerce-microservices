package com.monssef.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.ecommerce.auth.JwtAuthenticationFilter;
import com.monssef.ecommerce.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   ObjectMapper objectMapper) throws Exception {
        JsonSecurityErrorHandler errorHandler = new JsonSecurityErrorHandler(objectMapper);
        http
            .csrf(AbstractHttpConfigurer::disable) // no cookie session, so no CSRF risk
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Rules are checked in order: the first match wins
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll() // otherwise Spring Boot error pages turn into 401
                // Catalog: anyone can read, only ADMIN can write
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                .requestMatchers("/api/products/**", "/api/categories/**").hasRole("ADMIN")
                // Everything else (cart, orders...) needs a logged-in user, USER or ADMIN
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errorHandler) // 401
                .accessDeniedHandler(errorHandler))     // 403
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
