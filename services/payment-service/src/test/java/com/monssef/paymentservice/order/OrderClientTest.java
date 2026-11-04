package com.monssef.paymentservice.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.paymentservice.auth.ServiceTokenProvider;
import com.monssef.paymentservice.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// The HTTP calls go to a fake server: no Spring context, no Eureka, no order-service
class OrderClientTest {

    private static final String BASE_URL = "http://order-service";
    private static final String ORDER_JSON = """
            {"id":7,"userEmail":"john@example.com","status":"%s","total":1999.98}
            """;

    private MockRestServiceServer server;
    private OrderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrderClient(builder, new ServiceTokenProvider(TestTokens.SECRET, Duration.ofMinutes(5)),
                new ObjectMapper(), BASE_URL);
    }

    @Test
    void find_shouldReturnOwnerStatusAndTotal_withServiceToken() {
        server.expect(requestTo(BASE_URL + "/internal/orders/7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, startsWith("Bearer ey")))
                .andRespond(withSuccess(ORDER_JSON.formatted("PENDING"), MediaType.APPLICATION_JSON));

        OrderSummary order = client.find(7L);

        assertThat(order).isEqualTo(
                new OrderSummary(7L, "john@example.com", OrderStatus.PENDING, new BigDecimal("1999.98")));
        server.verify();
    }

    @Test
    void find_shouldPassOn404WithTheMessageOfOrderService() {
        server.expect(requestTo(BASE_URL + "/internal/orders/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"message\":\"Order with id 99 not found\"}"));

        assertThatThrownBy(() -> client.find(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order with id 99 not found")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markPaid_shouldPostAndReturnThePaidOrder() {
        server.expect(requestTo(BASE_URL + "/internal/orders/7/paid"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(ORDER_JSON.formatted("PAID"), MediaType.APPLICATION_JSON));

        assertThat(client.markPaid(7L).status()).isEqualTo(OrderStatus.PAID);
        server.verify();
    }

    @Test
    void markPaid_shouldPassOn409_whenOrderNotPending() {
        server.expect(requestTo(BASE_URL + "/internal/orders/7/paid"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"Order 7 is PAID, only a PENDING order can be paid\"}"));

        assertThatThrownBy(() -> client.markPaid(7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order 7 is PAID, only a PENDING order can be paid")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void find_shouldThrow503_whenOrderServiceFails() {
        server.expect(requestTo(BASE_URL + "/internal/orders/7")).andRespond(withServerError());

        assertUnavailable(() -> client.find(7L));
    }

    @Test
    void find_shouldThrow503_whenOrderServiceRejectsTheToken() {
        // A 401 / 403 is a configuration problem between services, not something the user can fix
        server.expect(requestTo(BASE_URL + "/internal/orders/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertUnavailable(() -> client.find(7L));
    }

    @Test
    void markPaid_shouldThrow503_whenOrderServiceIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/internal/orders/7/paid"))
                .andRespond(withException(new IOException("Connection refused")));

        assertUnavailable(() -> client.markPaid(7L));
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order service unavailable")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
