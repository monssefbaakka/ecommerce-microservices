package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.order.OrderSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    // Id of the order in order-service, without foreign key (another database).
    // An order can have several attempts, at most one SUCCEEDED (the order is then PAID)
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // Owner of the order, copied from order-service: the payment history is read without calling it
    @Column(nullable = false, length = 150)
    private String userEmail;

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

    public Payment(OrderSummary order, String cardNumber, PaymentStatus status) {
        this.orderId = order.id();
        this.userEmail = order.userEmail();
        this.amount = order.total();
        this.cardLast4 = cardNumber.substring(cardNumber.length() - 4);
        this.status = status;
    }
}
