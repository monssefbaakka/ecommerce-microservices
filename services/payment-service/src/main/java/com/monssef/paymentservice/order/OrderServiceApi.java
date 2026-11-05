package com.monssef.paymentservice.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * The /internal endpoints of order-service, as a Feign client. No url: "order-service" is
 * the Eureka name, resolved by the LoadBalancer on each call and spread over the instances.
 * Used through OrderClient, which turns a failed call into a 503.
 */
@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderServiceApi {

    @GetMapping("/{id}")
    OrderSummary find(@PathVariable("id") Long id);

    @PostMapping("/{id}/paid")
    OrderSummary markPaid(@PathVariable("id") Long id);
}
