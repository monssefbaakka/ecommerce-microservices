package com.monssef.paymentservice.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order row now lives in order-service: no SELECT ... FOR UPDATE on it from here.
 * A PostgreSQL advisory lock keyed by the order id takes its place, in payment_db: held until the
 * end of the current transaction, shared by every instance of payment-service.
 */
@Component
@RequiredArgsConstructor
public class OrderPaymentLock {

    private final JdbcTemplate jdbcTemplate;

    // A second payment of the same order waits here until the first one commits
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long orderId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, orderId);
    }
}
