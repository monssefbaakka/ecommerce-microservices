package com.monssef.ecommerce.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // Derived query: joins users and matches on users.email
    Optional<Cart> findByUserEmail(String email);
}
