package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.order.Order;
import com.monssef.paymentservice.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the entity: no Spring context, no database
class PaymentTest {

    @Test
    void newPayment_shouldChargeTheOrderTotal() {
        Order order = order("1999.98");

        Payment payment = new Payment(order, "4242424242424242", PaymentStatus.SUCCEEDED);

        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getAmount()).isEqualByComparingTo("1999.98");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void newPayment_shouldKeepOnlyTheLast4Digits() {
        Payment payment = new Payment(order("10.00"), "4000000000000002", PaymentStatus.DECLINED);

        assertThat(payment.getCardLast4()).isEqualTo("0002");
    }

    @Test
    void isPending_shouldBeTrueOnlyForPendingOrders() {
        Order order = order("10.00");
        assertThat(order.isPending()).isTrue();

        order.setStatus(OrderStatus.PAID);
        assertThat(order.isPending()).isFalse();

        order.setStatus(OrderStatus.CANCELLED);
        assertThat(order.isPending()).isFalse();
    }

    private Order order(String total) {
        Order order = new Order();
        order.setId(7L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotal(new BigDecimal(total));
        return order;
    }
}
