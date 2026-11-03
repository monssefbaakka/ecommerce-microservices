package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.order.Order;
import com.monssef.paymentservice.order.OrderStatus;
import com.monssef.paymentservice.payment.dto.PaymentDTO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the generated mapper: no Spring context, no database
class PaymentMapperTest {

    private final PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);

    @Test
    void toDto_shouldMapPaymentAndOrderId() {
        Order order = new Order();
        order.setId(7L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotal(new BigDecimal("1999.98"));
        Payment payment = new Payment(order, "4242424242424242", PaymentStatus.SUCCEEDED);
        payment.setId(3L);
        payment.setCreatedAt(Instant.parse("2026-11-03T10:00:00Z"));

        PaymentDTO dto = mapper.toDto(payment);

        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getOrderId()).isEqualTo(7L);
        assertThat(dto.getAmount()).isEqualByComparingTo("1999.98");
        assertThat(dto.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(dto.getCardLast4()).isEqualTo("4242");
        assertThat(dto.getCreatedAt()).isEqualTo(payment.getCreatedAt());
    }
}
