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
        // The HttpURLConnection under Feign sends a POST again by itself when the connection drops,
        // behind the retries of RemoteCalls: a POST must never reach order-service twice.
        // Read once, on the first HTTP call: set before anything starts
        System.setProperty("sun.net.http.retryPost", "false");
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
