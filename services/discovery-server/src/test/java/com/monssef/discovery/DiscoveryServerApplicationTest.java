package com.monssef.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The real server on a random port, called over HTTP like a service would
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registry_shouldAnswerInJson() {
        ResponseEntity<String> response = rest.exchange("/eureka/apps", HttpMethod.GET, jsonRequest(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // No assertion on the content: the other test may already have registered an instance
        assertThat(response.getBody()).startsWith("{\"applications\"");
    }

    @Test
    void registry_shouldReturnAnInstance_afterItRegisters() {
        // The same call a Eureka client makes at startup
        String instance = """
                {"instance": {
                  "instanceId": "localhost:test-service:9999",
                  "hostName": "localhost", "app": "TEST-SERVICE", "ipAddr": "127.0.0.1",
                  "status": "UP", "port": {"$": 9999, "@enabled": "true"},
                  "dataCenterInfo": {"@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo", "name": "MyOwn"}
                }}""";
        ResponseEntity<Void> registered = rest.exchange("/eureka/apps/TEST-SERVICE", HttpMethod.POST,
                jsonRequest(instance), Void.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> response = rest.exchange("/eureka/apps/TEST-SERVICE", HttpMethod.GET,
                jsonRequest(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("localhost:test-service:9999").contains("\"UP\"");
    }

    @Test
    void dashboard_shouldBeServed() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Eureka");
    }

    @Test
    void health_shouldBeUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void info_shouldDescribeTheServer() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("discovery-server").contains("\"build\"");
    }

    @Test
    void otherActuatorEndpoints_shouldNotBeExposed() {
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpEntity<String> jsonRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
