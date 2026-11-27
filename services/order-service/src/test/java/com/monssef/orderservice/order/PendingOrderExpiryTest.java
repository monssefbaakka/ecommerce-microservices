package com.monssef.orderservice.order;

import com.monssef.orderservice.client.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // plain unit test: the job on a mocked OrderService and a fixed clock
class PendingOrderExpiryTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-27T09:30:00Z"); // NOW - 30 min

    @Mock
    private OrderService orderService;

    private PendingOrderExpiry job() {
        return new PendingOrderExpiry(orderService, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30), 100);
    }

    @Test
    void cancelExpiredOrders_shouldExpireEachOrderPlacedBeforeTheDeadline() {
        when(orderService.findExpiredIds(DEADLINE, 100)).thenReturn(List.of(3L, 5L));
        when(orderService.expire(anyLong(), eq(DEADLINE))).thenReturn(true);

        job().cancelExpiredOrders();

        verify(orderService).expire(3L, DEADLINE);
        verify(orderService).expire(5L, DEADLINE);
    }

    @Test
    void cancelExpiredOrders_shouldDoNothing_whenNoOrderExpired() {
        when(orderService.findExpiredIds(DEADLINE, 100)).thenReturn(List.of());

        job().cancelExpiredOrders();

        verify(orderService, never()).expire(anyLong(), any());
    }

    @Test
    void cancelExpiredOrders_shouldGoOnWithTheNextOrders_whenOneFails() {
        when(orderService.findExpiredIds(DEADLINE, 100)).thenReturn(List.of(3L, 5L));
        when(orderService.expire(3L, DEADLINE)).thenThrow(new QueryTimeoutException("lock wait"));
        when(orderService.expire(5L, DEADLINE)).thenReturn(true);

        job().cancelExpiredOrders();

        verify(orderService).expire(5L, DEADLINE);
    }

    @Test
    void cancelExpiredOrders_shouldStop_whenProductServiceIsUnavailable() {
        when(orderService.findExpiredIds(DEADLINE, 100)).thenReturn(List.of(3L, 5L));
        when(orderService.expire(3L, DEADLINE)).thenThrow(new ServiceUnavailableException("Product service"));

        job().cancelExpiredOrders();

        // the next ones would fail the same way: left PENDING for the next run
        verify(orderService, never()).expire(5L, DEADLINE);
    }
}
