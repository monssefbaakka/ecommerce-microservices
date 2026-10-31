package com.monssef.ecommerce.order;

import com.monssef.ecommerce.order.dto.OrderDTO;
import com.monssef.ecommerce.order.dto.OrderItemDTO;
import com.monssef.ecommerce.product.Product;
import com.monssef.ecommerce.user.User;
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
        Order order = new Order(new User());
        order.setId(7L);
        order.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        order.addItem(product(1L, "iPhone 15", "999.99"), 2);
        order.addItem(product(2L, "Case", "19.90"), 1);

        OrderDTO dto = mapper.toDto(order);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(dto.getCreatedAt()).isEqualTo(order.getCreatedAt());
        assertThat(dto.getItems()).extracting(OrderItemDTO::getProductId).containsExactly(1L, 2L);
        assertThat(dto.getTotalQuantity()).isEqualTo(3);
        assertThat(dto.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void toDto_shouldKeepPriceCopiedAtPurchaseTime() {
        Product product = product(1L, "iPhone 15", "999.99");
        OrderItem item = new Order(new User()).addItem(product, 2);
        product.setName("iPhone 15 Pro");
        product.setPrice(new BigDecimal("1099.99"));

        OrderItemDTO dto = mapper.toDto(item);

        assertThat(dto.getProductName()).isEqualTo("iPhone 15");
        assertThat(dto.getUnitPrice()).isEqualByComparingTo("999.99");
        assertThat(dto.getSubtotal()).isEqualByComparingTo("1999.98");
    }

    @Test
    void toDto_shouldLeaveProductIdNull_whenProductDeleted() {
        OrderItem item = new Order(new User()).addItem(product(1L, "iPhone 15", "999.99"), 1);
        item.setProduct(null); // ON DELETE SET NULL

        OrderItemDTO dto = mapper.toDto(item);

        assertThat(dto.getProductId()).isNull();
        assertThat(dto.getProductName()).isEqualTo("iPhone 15");
    }

    private Product product(Long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
