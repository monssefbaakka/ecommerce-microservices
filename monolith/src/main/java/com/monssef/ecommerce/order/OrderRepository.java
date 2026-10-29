package com.monssef.ecommerce.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Derived query: the order must belong to the user, otherwise it is "not found"
    Optional<Order> findByIdAndUserEmail(Long id, String email);
}
