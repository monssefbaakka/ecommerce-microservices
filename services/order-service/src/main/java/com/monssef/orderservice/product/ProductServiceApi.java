package com.monssef.orderservice.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/**
 * The /internal endpoints of product-service, as a Feign client. No url: "product-service" is
 * the Eureka name, resolved by the LoadBalancer on each call and spread over the instances.
 * Used through ProductClient, which turns a failed call into a 503.
 */
@FeignClient(name = "product-service", path = "/internal", configuration = ProductServiceFeignConfig.class)
public interface ProductServiceApi {

    // Sent as ?ids=1&ids=2: one call for the whole cart
    @GetMapping("/products")
    List<ProductInfo> findAllById(@RequestParam("ids") Collection<Long> ids);

    @PostMapping("/stock/reservations")
    List<ReservedItem> reserve(@RequestBody StockRequest request);

    @PostMapping("/stock/releases")
    void release(@RequestBody StockRequest request);
}
