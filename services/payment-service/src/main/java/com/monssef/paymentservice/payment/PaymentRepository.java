package com.monssef.paymentservice.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Derived queries joined on orders and users: only the payments of the user's own orders
    Page<Payment> findByOrderUserEmail(String email, Pageable pageable);

    Optional<Payment> findByIdAndOrderUserEmail(Long id, String email);
}
