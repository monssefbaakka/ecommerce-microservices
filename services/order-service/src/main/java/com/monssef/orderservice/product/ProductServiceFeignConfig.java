package com.monssef.orderservice.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.orderservice.auth.ServiceTokenInterceptor;
import com.monssef.orderservice.auth.ServiceTokenProvider;
import com.monssef.orderservice.client.RemoteErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Beans of the product-service Feign client only. Not a @Configuration on purpose: picked up by the
 * component scan, its beans would apply to every Feign client of the service.
 */
public class ProductServiceFeignConfig {

    @Bean
    RequestInterceptor serviceTokenInterceptor(ServiceTokenProvider tokens) {
        return new ServiceTokenInterceptor(tokens);
    }

    @Bean
    ErrorDecoder productServiceErrorDecoder(ObjectMapper objectMapper) {
        return new RemoteErrorDecoder("Product service", objectMapper);
    }
}
