package com.monssef.ecommerce.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Derived query: the order must belong to the user, otherwise it is "not found"
    Optional<Order> findByIdAndUserEmail(Long id, String email);

    // Order history: only the orders of the user, page and sort chosen by the caller
    Page<Order> findByUserEmail(String email, Pageable pageable);
}
