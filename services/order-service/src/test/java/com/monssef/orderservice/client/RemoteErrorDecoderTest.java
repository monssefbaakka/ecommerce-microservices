package com.monssef.orderservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteErrorDecoderTest {

    private final RemoteErrorDecoder decoder = new RemoteErrorDecoder("Product service", new ObjectMapper());

    @Test
    void decode_shouldPassOn409WithTheMessageOfTheOtherService() {
        Exception decoded = decoder.decode("reserve", response(409, """
                {"status":409,"error":"Conflict","message":"Not enough stock for product 1 (iPhone 15): 1 left"}
                """));

        assertThat(decoded).isInstanceOf(ResponseStatusException.class)
                .isNotInstanceOf(ServiceUnavailableException.class);
        assertThat(((ResponseStatusException) decoded).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ResponseStatusException) decoded).getReason())
                .isEqualTo("Not enough stock for product 1 (iPhone 15): 1 left");
    }

    @Test
    void decode_shouldPassOn404WithTheMessageOfTheOtherService() {
        Exception decoded = decoder.decode("reserve", response(404,
                "{\"status\":404,\"message\":\"Product with id 1 not found\"}"));

        assertThat(((ResponseStatusException) decoded).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ResponseStatusException) decoded).getReason()).isEqualTo("Product with id 1 not found");
    }

    @Test
    void decode_shouldKeepThe404_whenTheBodyIsNotJson() {
        Exception decoded = decoder.decode("reserve", response(404, "<html>Not Found</html>"));

        assertThat(((ResponseStatusException) decoded).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ResponseStatusException) decoded).getReason()).isNull();
    }

    @Test
    void decode_shouldKeepThe409_whenThereIsNoBody() {
        Exception decoded = decoder.decode("reserve", response(409, null));

        assertThat(((ResponseStatusException) decoded).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ResponseStatusException) decoded).getReason()).isNull();
    }

    @Test
    void decode_shouldGive503_whenTheOtherServiceFails() {
        assertUnavailable(decoder.decode("findAllById", response(500, "{\"status\":500}")));
    }

    @Test
    void decode_shouldGive503_whenTheServiceTokenIsRejected() {
        // A 401 / 403 is a configuration problem between services, not something the user can fix
        assertUnavailable(decoder.decode("findAllById", response(401, null)));
        assertUnavailable(decoder.decode("findAllById", response(403, null)));
    }

    @Test
    void decode_shouldGive503_whenTheLoadBalancerHasNoInstance() {
        // The answer of the Feign LoadBalancer client when nothing is registered under the name
        assertUnavailable(decoder.decode("findAllById", response(503,
                "Load balancer does not contain an instance for the service product-service")));
    }

    private static void assertUnavailable(Exception decoded) {
        assertThat(decoded).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable");
        assertThat(((ResponseStatusException) decoded).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "http://product-service/internal/stock/reservations",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response.Builder response = Response.builder().status(status).request(request).headers(Map.of());
        if (body != null) {
            response.body(body, StandardCharsets.UTF_8);
        }
        return response.build();
    }
}
