package com.monssef.paymentservice.config;

import com.monssef.paymentservice.client.RemoteCalls;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The circuit breaker and the retries of order-service, set under resilience4j in application.yml.
 * The registries come from the Resilience4j starter and build each instance from its yml entry.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    RemoteCalls orderServiceCalls(CircuitBreakerRegistry breakers, RetryRegistry retries) {
        return new RemoteCalls("Order service", breakers.circuitBreaker("order-service"),
                retries.retry("order-service-reads"), retries.retry("order-service-writes"));
    }
}
