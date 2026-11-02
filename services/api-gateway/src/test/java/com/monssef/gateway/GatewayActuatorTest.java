package com.monssef.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

// Health checks are answered by the gateway itself, never routed to a service
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class GatewayActuatorTest {

    @Autowired
    private WebTestClient client;

    @Test
    void health_shouldBeUp() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void probes_shouldBeUp() {
        client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
    }

    @Test
    void info_shouldDescribeTheGateway() {
        client.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.app.name").isEqualTo("api-gateway")
                .jsonPath("$.build.artifact").isEqualTo("api-gateway");
    }

    @Test
    void routesEndpoint_shouldNotBeExposed() {
        client.get().uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isNotFound();
    }
}
