package com.monssef.orderservice.order;

import com.monssef.orderservice.client.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cancels the orders left unpaid for longer than orders.pending-expiry.timeout and gives their units
 * back to product-service: without it, an order nobody pays keeps its stock forever.
 * Each order is cancelled in its own transaction, under the same row lock as a payment: an order paid
 * while the job runs is left alone. Every instance runs the job; two instances on the same order wait
 * for each other's lock, and the second one finds it CANCELLED already.
 */
@Slf4j
@Component
public class PendingOrderExpiry {

    private final OrderService orderService;
    private final Clock clock;
    private final Duration timeout;
    private final int batchSize;

    public PendingOrderExpiry(OrderService orderService,
                              Clock clock,
                              @Value("${orders.pending-expiry.timeout}") Duration timeout,
                              @Value("${orders.pending-expiry.batch-size}") int batchSize) {
        this.orderService = orderService;
        this.clock = clock;
        this.timeout = timeout;
        this.batchSize = batchSize;
    }

    // Starts one interval after startup, then waits the interval after the end of each run
    @Scheduled(initialDelayString = "${orders.pending-expiry.check-interval}",
            fixedDelayString = "${orders.pending-expiry.check-interval}")
    public void cancelExpiredOrders() {
        Instant deadline = clock.instant().minus(timeout);
        List<Long> ids = orderService.findExpiredIds(deadline, batchSize);
        int cancelled = 0;
        for (Long id : ids) {
            try {
                if (orderService.expire(id, deadline)) {
                    cancelled++;
                }
            } catch (ServiceUnavailableException e) {
                // product-service down: the next orders would fail the same way. Still PENDING, next run
                log.warn("Product service unavailable: expired orders not cancelled, tried again on the next run");
                break;
            } catch (RuntimeException e) {
                log.warn("Expired order {} not cancelled, tried again on the next run", id, e);
            }
        }
        if (cancelled > 0) {
            log.info("{} unpaid order(s) older than {} cancelled, their stock given back", cancelled, timeout);
        }
    }
}
