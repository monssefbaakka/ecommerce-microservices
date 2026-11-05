package com.monssef.paymentservice.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.paymentservice.auth.ServiceTokenInterceptor;
import com.monssef.paymentservice.auth.ServiceTokenProvider;
import com.monssef.paymentservice.client.RemoteErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Beans of the order-service Feign client only. Not a @Configuration on purpose: picked up by the
 * component scan, its beans would apply to every Feign client of the service.
 */
public class OrderServiceFeignConfig {

    @Bean
    RequestInterceptor serviceTokenInterceptor(ServiceTokenProvider tokens) {
        return new ServiceTokenInterceptor(tokens);
    }

    @Bean
    ErrorDecoder orderServiceErrorDecoder(ObjectMapper objectMapper) {
        return new RemoteErrorDecoder("Order service", objectMapper);
    }
}
