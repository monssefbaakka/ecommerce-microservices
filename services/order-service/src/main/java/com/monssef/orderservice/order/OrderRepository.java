package com.monssef.orderservice.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Derived query: the order must belong to the user, otherwise it is "not found"
    Optional<Order> findByIdAndUserEmail(Long id, String email);

    // Order history: only the orders of the user, page and sort chosen by the caller
    Page<Order> findByUserEmail(String email, Pageable pageable);

    // Row lock (SELECT ... FOR UPDATE) until the end of the transaction, for the payment of the order
    // or its expiry
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findForUpdateById(@Param("id") Long id);

    // Same lock, for a cancellation by the user: someone else's order is "not found"
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.userEmail = :email")
    Optional<Order> findForUpdateByIdAndUserEmail(@Param("id") Long id, @Param("email") String email);

    // Ids only, oldest first, no lock: each order is then locked and checked again on its own
    // (idx_orders_pending_created_at). The page only bounds how many are read per run
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.createdAt < :before ORDER BY o.createdAt, o.id")
    List<Long> findIdsByStatusCreatedBefore(@Param("status") OrderStatus status,
                                            @Param("before") Instant before,
                                            Pageable page);
}
