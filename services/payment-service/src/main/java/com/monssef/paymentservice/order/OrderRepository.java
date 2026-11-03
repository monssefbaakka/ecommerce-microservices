package com.monssef.paymentservice.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // The order must belong to the user, otherwise it is "not found". Row lock (SELECT ... FOR UPDATE)
    // held until the end of the transaction: a second payment of the same order waits, then finds it PAID
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findForUpdateByIdAndUserEmail(Long id, String email);
}
