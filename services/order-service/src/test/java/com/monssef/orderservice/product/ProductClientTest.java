package com.monssef.orderservice.product;

import com.monssef.orderservice.client.RemoteCalls;
import com.monssef.orderservice.client.ServiceUnavailableException;
import com.monssef.orderservice.common.ResourceNotFoundException;
import com.monssef.orderservice.support.FeignFailures;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The Feign client is mocked: what goes over HTTP is tested in ProductServiceApiTest,
// the circuit breaker and the retries in RemoteCallsTest (here: one attempt, never opened)
@ExtendWith(MockitoExtension.class)
class ProductClientTest {

    private static final ProductInfo IPHONE = new ProductInfo(1L, "iPhone 15", new BigDecimal("999.99"), 10);
    private static final ProductInfo CASE = new ProductInfo(2L, "Case", new BigDecimal("19.90"), 0);

    @Mock
    private ProductServiceApi api;

    private final LastKnownProducts lastKnown = new LastKnownProducts();

    private ProductClient client;

    @BeforeEach
    void setUp() {
        Retry once = Retry.of("once", RetryConfig.custom().maxAttempts(1).build());
        client = new ProductClient(api, new RemoteCalls("Product service", CircuitBreaker.ofDefaults("product-service"),
                once, once), lastKnown);
    }

    @Test
    void findAll_shouldAskForEveryIdInOneCall_andIndexByTheId() {
        when(api.findAllById(List.of(1L, 2L))).thenReturn(List.of(IPHONE, CASE));

        Map<Long, ProductInfo> products = client.findAll(List.of(1L, 2L));

        assertThat(products).containsOnlyKeys(1L, 2L);
        assertThat(products.get(1L)).isEqualTo(IPHONE);
        assertThat(products.get(2L).stock()).isZero();
    }

    @Test
    void findAll_shouldNotCallProductService_whenNoIds() {
        assertThat(client.findAll(List.of())).isEmpty();

        verifyNoInteractions(api);
    }

    @Test
    void find_shouldReturnTheProduct() {
        when(api.findAllById(List.of(1L))).thenReturn(List.of(IPHONE));

        assertThat(client.find(1L)).isEqualTo(IPHONE);
    }

    @Test
    void find_shouldThrow404_whenProductMissingFromTheAnswer() {
        when(api.findAllById(List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> client.find(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void reserve_shouldSendTheLinesAndReturnWhatWasReserved() {
        List<StockLine> lines = List.of(new StockLine(1L, 2));
        ReservedItem reserved = new ReservedItem(1L, "iPhone 15", new BigDecimal("999.99"), 2);
        when(api.reserve(new StockRequest(lines))).thenReturn(List.of(reserved));

        assertThat(client.reserve(lines)).containsExactly(reserved);
    }

    @Test
    void reserve_shouldLetThe409OfTheDecoderThrough() {
        ResponseStatusException conflict = new ResponseStatusException(HttpStatus.CONFLICT,
                "Not enough stock for product 1 (iPhone 15): 1 left");
        when(api.reserve(any())).thenThrow(conflict);

        assertThatThrownBy(() -> client.reserve(List.of(new StockLine(1L, 2)))).isSameAs(conflict);
    }

    @Test
    void release_shouldSendTheLines() {
        List<StockLine> lines = List.of(new StockLine(1L, 2));

        client.release(lines);

        verify(api).release(new StockRequest(lines));
    }

    @Test
    void findAll_shouldThrow503_whenProductServiceIsUnreachable() {
        when(api.findAllById(any())).thenThrow(FeignFailures.connectionRefused());

        assertUnavailable(() -> client.findAll(List.of(1L)));
    }

    @Test
    void findAll_shouldRememberTheAnswer() {
        when(api.findAllById(List.of(1L, 2L))).thenReturn(List.of(IPHONE, CASE));

        client.findAll(List.of(1L, 2L));

        assertThat(lastKnown.get(List.of(1L, 2L))).containsOnlyKeys(1L, 2L);
    }

    @Test
    void findAllOrLastKnown_shouldReturnTheAnswerAsUpToDate_whenProductServiceAnswers() {
        when(api.findAllById(List.of(1L))).thenReturn(List.of(IPHONE));

        ProductSnapshot snapshot = client.findAllOrLastKnown(List.of(1L));

        assertThat(snapshot.upToDate()).isTrue();
        assertThat(snapshot.products()).containsEntry(1L, IPHONE);
    }

    @Test
    void findAllOrLastKnown_shouldReturnTheLastKnownProducts_whenProductServiceIsUnreachable() {
        when(api.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(IPHONE, CASE))
                .thenThrow(FeignFailures.connectionRefused());
        client.findAll(List.of(1L, 2L));

        ProductSnapshot snapshot = client.findAllOrLastKnown(List.of(1L, 2L));

        assertThat(snapshot.upToDate()).isFalse();
        assertThat(snapshot.products()).containsEntry(1L, IPHONE).containsEntry(2L, CASE);
    }

    @Test
    void findAllOrLastKnown_shouldReturnNoProduct_whenProductServiceIsUnreachableAndNoneWasSeen() {
        when(api.findAllById(any())).thenThrow(FeignFailures.connectionRefused());

        ProductSnapshot snapshot = client.findAllOrLastKnown(List.of(1L));

        assertThat(snapshot.upToDate()).isFalse();
        assertThat(snapshot.products()).isEmpty();
    }

    @Test
    void findAllOrLastKnown_shouldNotHideA404OfProductService() {
        // Only "unavailable" falls back: any other error is not about product-service being down
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown");
        when(api.findAllById(any())).thenThrow(notFound);

        assertThatThrownBy(() -> client.findAllOrLastKnown(List.of(1L))).isSameAs(notFound);
    }

    @Test
    void release_shouldThrow503_whenProductServiceIsUnreachable() {
        doThrow(FeignFailures.connectionRefused()).when(api).release(any());

        assertUnavailable(() -> client.release(List.of(new StockLine(1L, 2))));
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
