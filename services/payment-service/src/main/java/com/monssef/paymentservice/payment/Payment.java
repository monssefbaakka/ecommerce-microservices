package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

// One row per attempt, declined ones included: the history shows every try on an order
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // An order can have several attempts, at most one SUCCEEDED (the order is then PAID)
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Copied from the order total at payment time, same precision
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // Never the full card number: only the last 4 digits are kept, to show which card was used
    @Column(nullable = false, length = 4)
    private String cardLast4;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Payment(Order order, String cardNumber, PaymentStatus status) {
        this.order = order;
        this.amount = order.getTotal();
        this.cardLast4 = cardNumber.substring(cardNumber.length() - 4);
        this.status = status;
    }
}
