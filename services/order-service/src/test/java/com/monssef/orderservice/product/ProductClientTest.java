package com.monssef.orderservice.product;

import com.monssef.orderservice.client.ServiceUnavailableException;
import com.monssef.orderservice.common.ResourceNotFoundException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The Feign client is mocked: what goes over HTTP is tested in ProductServiceApiTest
@ExtendWith(MockitoExtension.class)
class ProductClientTest {

    private static final ProductInfo IPHONE = new ProductInfo(1L, "iPhone 15", new BigDecimal("999.99"), 10);
    private static final ProductInfo CASE = new ProductInfo(2L, "Case", new BigDecimal("19.90"), 0);

    @Mock
    private ProductServiceApi api;

    @InjectMocks
    private ProductClient client;

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
        when(api.findAllById(any())).thenThrow(connectionRefused());

        assertUnavailable(() -> client.findAll(List.of(1L)));
    }

    @Test
    void release_shouldThrow503_whenProductServiceIsUnreachable() {
        doThrow(connectionRefused()).when(api).release(any());

        assertUnavailable(() -> client.release(List.of(new StockLine(1L, 2))));
    }

    // What Feign throws when the connection fails
    private static FeignException connectionRefused() {
        Request request = Request.create(Request.HttpMethod.GET, "http://product-service/internal/products",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request);
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Product service unavailable")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
