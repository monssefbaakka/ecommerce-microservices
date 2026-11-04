package com.monssef.orderservice.order;

import com.monssef.orderservice.product.ReservedItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the entity logic: no Spring context, no database
class OrderTest {

    @Test
    void newOrder_shouldBePendingAndEmpty() {
        Order order = new Order("alice@example.com");

        assertThat(order.getUserEmail()).isEqualTo("alice@example.com");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getTotal()).isEqualByComparingTo("0");
        assertThat(order.getTotalQuantity()).isZero();
    }

    @Test
    void addItem_shouldLinkItemToOrderAndUpdateTotal() {
        Order order = new Order("alice@example.com");

        OrderItem item = order.addItem(reserved(1L, "iPhone 15", "999.99", 2));
        order.addItem(reserved(2L, "Case", "19.90", 1));

        assertThat(item.getOrder()).isSameAs(order);
        assertThat(item.getSubtotal()).isEqualByComparingTo("1999.98");
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalQuantity()).isEqualTo(3);
        assertThat(order.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void addItem_shouldCopyWhatProductServiceReserved() {
        OrderItem item = new Order("alice@example.com").addItem(reserved(1L, "iPhone 15", "999.99", 2));

        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getProductName()).isEqualTo("iPhone 15");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("999.99");
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    private ReservedItem reserved(Long productId, String name, String price, int quantity) {
        return new ReservedItem(productId, name, new BigDecimal(price), quantity);
    }
}
