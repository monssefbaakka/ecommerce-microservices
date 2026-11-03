package com.monssef.paymentservice.support;

import com.monssef.paymentservice.order.OrderClient;
import com.monssef.paymentservice.order.OrderStatus;
import com.monssef.paymentservice.order.OrderSummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * order-service in memory, behind the mocked OrderClient of the integration tests:
 * the same answers (404, 409 when not PENDING) without Eureka or a second service.
 * Synchronized: the concurrency test pays from several threads at once.
 */
public class FakeOrderService {

    private final Map<Long, OrderSummary> orders = new HashMap<>();
    private long nextId = 1;
    private int markPaidCalls;

    // A PENDING order as order-service places it
    public synchronized long create(String email, String total) {
        long id = nextId++;
        orders.put(id, new OrderSummary(id, email, OrderStatus.PENDING, new BigDecimal(total)));
        return id;
    }

    public synchronized void setStatus(long id, OrderStatus status) {
        OrderSummary order = orders.get(id);
        orders.put(id, new OrderSummary(id, order.userEmail(), status, order.total()));
    }

    public synchronized String statusOf(long id) {
        return orders.get(id).status().name();
    }

    public synchronized int markPaidCalls() {
        return markPaidCalls;
    }

    public void answerFor(OrderClient client) {
        when(client.find(anyLong())).thenAnswer(call -> find(call.getArgument(0)));
        when(client.markPaid(anyLong())).thenAnswer(call -> markPaid(call.getArgument(0)));
    }

    private synchronized OrderSummary find(Long id) {
        OrderSummary order = orders.get(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id %d not found".formatted(id));
        }
        return order;
    }

    private synchronized OrderSummary markPaid(Long id) {
        markPaidCalls++;
        OrderSummary order = find(id);
        if (!order.isPending()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order %d is %s, only a PENDING order can be paid".formatted(id, order.status()));
        }
        setStatus(id, OrderStatus.PAID);
        return orders.get(id);
    }
}
