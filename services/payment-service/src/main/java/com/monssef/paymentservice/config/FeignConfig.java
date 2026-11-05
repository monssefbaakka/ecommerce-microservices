package com.monssef.paymentservice.config;

import com.monssef.paymentservice.PaymentServiceApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Turns every @FeignClient interface of the service into a bean. A client without a url is resolved
 * by its name through the LoadBalancer and Eureka, like lb:// in the gateway.
 * Kept off the application class: @WebMvcTest slices would otherwise try to build the clients.
 */
@Configuration
@EnableFeignClients(basePackageClasses = PaymentServiceApplication.class)
public class FeignConfig {
}
