package com.monssef.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.paymentservice.auth.JwtAuthenticationFilter;
import com.monssef.paymentservice.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                // Payments: any logged-in user (USER or ADMIN), each only pays and sees their own.
                // Anything not listed above needs a token: nothing is open by mistake
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errorHandler) // 401
                .accessDeniedHandler(errorHandler))     // 403
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
