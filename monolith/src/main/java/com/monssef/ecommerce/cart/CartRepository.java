package com.monssef.ecommerce.cart;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // Derived query: joins users and matches on users.email
    Optional<Cart> findByUserEmail(String email);

    // Same, with a row lock (SELECT ... FOR UPDATE) held until the end of the transaction:
    // a second order from the same cart waits, then finds the cart already emptied
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cart> findForUpdateByUserEmail(String email);
}
