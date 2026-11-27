package com.monssef.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

// Runs the @Scheduled jobs (PendingOrderExpiry) on one background thread of each instance
@Configuration
@EnableScheduling
public class SchedulingConfig {

    // The time the jobs read: a fixed one in their unit tests
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
