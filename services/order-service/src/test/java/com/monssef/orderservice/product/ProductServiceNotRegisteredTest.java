package com.monssef.orderservice.product;

import com.monssef.orderservice.client.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// No instance of product-service known to the LoadBalancer: what happens before it registers in Eureka
@SpringBootTest(classes = ProductServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class ProductServiceNotRegisteredTest {

    @Autowired
    private ProductClient client;

    @Test
    void findAll_shouldThrow503_whenProductServiceIsNotRegistered() {
        assertThatThrownBy(() -> client.findAll(List.of(1L)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable");
    }

    @Test
    void reserve_shouldThrow503_whenProductServiceIsNotRegistered() {
        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
