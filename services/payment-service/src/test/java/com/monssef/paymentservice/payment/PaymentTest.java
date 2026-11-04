package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.order.OrderStatus;
import com.monssef.paymentservice.order.OrderSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the entity and the order summary: no Spring context, no database
class PaymentTest {

    @Test
    void newPayment_shouldChargeTheOrderTotalAndKeepOrderIdAndOwner() {
        Payment payment = new Payment(order(OrderStatus.PENDING), "4242424242424242", PaymentStatus.SUCCEEDED);

        assertThat(payment.getOrderId()).isEqualTo(7L);
        assertThat(payment.getUserEmail()).isEqualTo("john@example.com");
        assertThat(payment.getAmount()).isEqualByComparingTo("1999.98");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void newPayment_shouldKeepOnlyTheLast4Digits() {
        Payment payment = new Payment(order(OrderStatus.PENDING), "4000000000000002", PaymentStatus.DECLINED);

        assertThat(payment.getCardLast4()).isEqualTo("0002");
    }

    @Test
    void isPending_shouldBeTrueOnlyForPendingOrders() {
        assertThat(order(OrderStatus.PENDING).isPending()).isTrue();
        assertThat(order(OrderStatus.PAID).isPending()).isFalse();
        assertThat(order(OrderStatus.CANCELLED).isPending()).isFalse();
    }

    @Test
    void belongsTo_shouldCompareTheOwnerEmail() {
        assertThat(order(OrderStatus.PENDING).belongsTo("john@example.com")).isTrue();
        assertThat(order(OrderStatus.PENDING).belongsTo("jane@example.com")).isFalse();
    }

    private OrderSummary order(OrderStatus status) {
        return new OrderSummary(7L, "john@example.com", status, new BigDecimal("1999.98"));
    }
}
