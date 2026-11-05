package com.monssef.paymentservice.order;

import com.monssef.paymentservice.client.ServiceUnavailableException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * What a payment needs from order-service, which owns the orders.
 * The calls go through the OrderServiceApi Feign client, with a SERVICE token. A 404 or 409 of
 * order-service is passed on with its message (RemoteErrorDecoder); order-service down,
 * not registered or failing gives a 503.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderClient {

    private static final String SERVICE = "Order service";

    private final OrderServiceApi api;

    // Owner, status and total of the order: 404 if unknown
    public OrderSummary find(Long id) {
        return call(() -> api.find(id));
    }

    // PENDING -> PAID once the card is accepted: 409 if the order is not PENDING any more
    public OrderSummary markPaid(Long id) {
        return call(() -> api.markPaid(id));
    }

    // Connection refused, timeout or unreadable answer. The error answers are already turned
    // into a ResponseStatusException by the decoder, and go through untouched
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (FeignException e) {
            log.warn("order-service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException(SERVICE);
        }
    }
}
