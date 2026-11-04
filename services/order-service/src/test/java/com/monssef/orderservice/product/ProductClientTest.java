package com.monssef.orderservice.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.orderservice.auth.ServiceTokenProvider;
import com.monssef.orderservice.common.ResourceNotFoundException;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// The HTTP calls go to a fake server: no Spring context, no Eureka, no product-service
class ProductClientTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC0xMjM=";
    private static final String BASE_URL = "http://product-service";

    private MockRestServiceServer server;
    private ProductClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProductClient(builder, new ServiceTokenProvider(SECRET, Duration.ofMinutes(5)),
                new ObjectMapper(), BASE_URL);
    }

    @Test
    void findAll_shouldAskForEveryIdInOneCall_withServiceToken() {
        server.expect(requestTo(BASE_URL + "/internal/products?ids=1&ids=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ey")))
                .andRespond(withSuccess("""
                        [{"id":1,"name":"iPhone 15","description":"Phone","price":999.99,"stock":10},
                         {"id":2,"name":"Case","description":null,"price":19.90,"stock":0}]
                        """, MediaType.APPLICATION_JSON));

        Map<Long, ProductInfo> products = client.findAll(List.of(1L, 2L));

        assertThat(products).containsOnlyKeys(1L, 2L);
        assertThat(products.get(1L).name()).isEqualTo("iPhone 15");
        assertThat(products.get(1L).price()).isEqualByComparingTo("999.99");
        assertThat(products.get(2L).stock()).isZero();
        server.verify();
    }

    @Test
    void findAll_shouldNotCallProductService_whenNoIds() {
        assertThat(client.findAll(List.of())).isEmpty();

        server.verify(); // no request expected
    }

    @Test
    void find_shouldThrow404_whenProductMissingFromTheAnswer() {
        server.expect(requestTo(BASE_URL + "/internal/products?ids=99"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.find(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void reserve_shouldPostTheLinesAndReturnWhatWasReserved() {
        server.expect(requestTo(BASE_URL + "/internal/stock/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andRespond(withSuccess("""
                        [{"productId":1,"name":"iPhone 15","price":999.99,"quantity":2}]
                        """, MediaType.APPLICATION_JSON));

        List<ReservedItem> reserved = client.reserve(List.of(new StockLine(1L, 2)));

        assertThat(reserved).containsExactly(new ReservedItem(1L, "iPhone 15", new BigDecimal("999.99"), 2));
        server.verify();
    }

    @Test
    void reserve_shouldPassOn409WithTheMessageOfProductService() {
        server.expect(requestTo(BASE_URL + "/internal/stock/reservations"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"status":409,"error":"Conflict",
                         "message":"Not enough stock for product 1 (iPhone 15): 1 left","path":"/internal/stock/reservations"}
                        """));

        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough stock for product 1 (iPhone 15): 1 left")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reserve_shouldPassOn404_whenProductDeleted() {
        server.expect(requestTo(BASE_URL + "/internal/stock/reservations"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"message\":\"Product with id 1 not found\"}"));

        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product with id 1 not found")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reserve_shouldThrow503_whenProductServiceFails() {
        server.expect(requestTo(BASE_URL + "/internal/stock/reservations"))
                .andRespond(withServerError());

        assertUnavailable(() -> client.reserve(List.of(new StockLine(1L, 2))));
    }

    @Test
    void findAll_shouldThrow503_whenProductServiceRejectsTheToken() {
        // A 401 / 403 is a configuration problem between services, not something the user can fix
        server.expect(requestTo(BASE_URL + "/internal/products?ids=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertUnavailable(() -> client.findAll(List.of(1L)));
    }

    @Test
    void findAll_shouldThrow503_whenProductServiceIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/internal/products?ids=1"))
                .andRespond(withException(new IOException("Connection refused")));

        assertUnavailable(() -> client.findAll(List.of(1L)));
    }

    @Test
    void release_shouldPostTheLines() {
        server.expect(requestTo(BASE_URL + "/internal/stock/releases"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andRespond(withNoContent());

        client.release(List.of(new StockLine(1L, 2)));

        server.verify();
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product service unavailable")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
