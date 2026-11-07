package com.monssef.paymentservice.order;

import com.monssef.paymentservice.client.RemoteCalls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What a payment needs from order-service, which owns the orders.
 * The calls go through the OrderServiceApi Feign client, with a SERVICE token, and through
 * RemoteCalls: circuit breaker and retries. A 404 or 409 of order-service is passed on with its
 * message (RemoteErrorDecoder); order-service down, not registered or failing gives a 503.
 */
@Component
@RequiredArgsConstructor
public class OrderClient {

    private final OrderServiceApi api;
    private final RemoteCalls calls;

    // Owner, status and total of the order: 404 if unknown
    public OrderSummary find(Long id) {
        return calls.read(() -> api.find(id));
    }

    // PENDING -> PAID once the card is accepted: 409 if the order is not PENDING any more
    public OrderSummary markPaid(Long id) {
        return calls.write(() -> api.markPaid(id));
    }
}
