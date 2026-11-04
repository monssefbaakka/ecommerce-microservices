package com.monssef.paymentservice.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Only the payments of the user's own orders, by the email copied at payment time
    Page<Payment> findByUserEmail(String email, Pageable pageable);

    Optional<Payment> findByIdAndUserEmail(Long id, String email);
}
