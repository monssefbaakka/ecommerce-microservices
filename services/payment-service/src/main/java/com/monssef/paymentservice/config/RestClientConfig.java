package com.monssef.paymentservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // @LoadBalanced: "http://order-service" is resolved through Eureka, like lb:// in the gateway,
    // and spread over the instances. Replaced by OpenFeign clients in #48
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
