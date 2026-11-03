package com.monssef.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

// No accounts here (the JWT carries the identity): no default user with a generated password
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
// Same JSON as the monolith for Page<>: { "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
