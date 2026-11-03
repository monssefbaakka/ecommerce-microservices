package com.monssef.paymentservice.order;

import com.monssef.paymentservice.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The orders placed in order-service. Only what a payment needs is mapped: the owner, the total to charge
 * and the status, the one column this service writes (PENDING -> PAID).
 */
@Entity
@Table(name = "orders") // "order" is a reserved word in SQL
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    // Frozen when the order was placed: the amount charged, never sent by the client
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal total;

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }
}
