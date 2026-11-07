package com.monssef.orderservice.config;

import com.monssef.orderservice.client.RemoteCalls;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The circuit breaker and the retries of product-service, set under resilience4j in application.yml.
 * The registries come from the Resilience4j starter and build each instance from its yml entry.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    RemoteCalls productServiceCalls(CircuitBreakerRegistry breakers, RetryRegistry retries) {
        return new RemoteCalls("Product service", breakers.circuitBreaker("product-service"),
                retries.retry("product-service-reads"), retries.retry("product-service-writes"));
    }
}
