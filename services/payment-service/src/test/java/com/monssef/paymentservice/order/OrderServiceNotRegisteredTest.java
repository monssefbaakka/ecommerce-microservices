package com.monssef.paymentservice.order;

import com.monssef.paymentservice.client.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// No instance of order-service known to the LoadBalancer: what happens before it registers in Eureka
@SpringBootTest(classes = OrderServiceApiTest.FeignClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"eureka.client.enabled=false",
                // No database here: the readiness group of application.yml asks for the db health
                "management.endpoint.health.validate-group-membership=false"})
class OrderServiceNotRegisteredTest {

    @Autowired
    private OrderClient client;

    @Test
    void find_shouldThrow503_whenOrderServiceIsNotRegistered() {
        assertThatThrownBy(() -> client.find(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Order service unavailable");
    }

    @Test
    void markPaid_shouldThrow503_whenOrderServiceIsNotRegistered() {
        assertThatThrownBy(() -> client.markPaid(7L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
