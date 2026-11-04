package com.monssef.orderservice.order;

import com.monssef.orderservice.order.dto.OrderDTO;
import com.monssef.orderservice.order.dto.OrderItemDTO;
import com.monssef.orderservice.order.dto.OrderSummaryDTO;
import com.monssef.orderservice.product.ReservedItem;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the generated mapper: no Spring context, no database
class OrderMapperTest {

    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void toDto_shouldMapOrderAndItems() {
        Order order = order();

        OrderDTO dto = mapper.toDto(order);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(dto.getCreatedAt()).isEqualTo(order.getCreatedAt());
        assertThat(dto.getItems()).extracting(OrderItemDTO::getProductId).containsExactly(1L, 2L);
        assertThat(dto.getTotalQuantity()).isEqualTo(3);
        assertThat(dto.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void toDto_shouldMapCopiedNameAndPrice() {
        OrderItem item = new Order("alice@example.com")
                .addItem(new ReservedItem(1L, "iPhone 15", new BigDecimal("999.99"), 2));

        OrderItemDTO dto = mapper.toDto(item);

        assertThat(dto.getProductId()).isEqualTo(1L);
        assertThat(dto.getProductName()).isEqualTo("iPhone 15");
        assertThat(dto.getUnitPrice()).isEqualByComparingTo("999.99");
        assertThat(dto.getQuantity()).isEqualTo(2);
        assertThat(dto.getSubtotal()).isEqualByComparingTo("1999.98");
    }

    @Test
    void toSummary_shouldMapOwnerStatusAndTotal() {
        OrderSummaryDTO dto = mapper.toSummary(order());

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getUserEmail()).isEqualTo("alice@example.com");
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(dto.getTotal()).isEqualByComparingTo("2019.88");
    }

    private Order order() {
        Order order = new Order("alice@example.com");
        order.setId(7L);
        order.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        order.addItem(new ReservedItem(1L, "iPhone 15", new BigDecimal("999.99"), 2));
        order.addItem(new ReservedItem(2L, "Case", new BigDecimal("19.90"), 1));
        return order;
    }
}
