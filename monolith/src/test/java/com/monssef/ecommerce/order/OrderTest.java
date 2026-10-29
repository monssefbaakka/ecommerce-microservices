package com.monssef.ecommerce.order;

import com.monssef.ecommerce.product.Product;
import com.monssef.ecommerce.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the entity logic: no Spring context, no database
class OrderTest {

    @Test
    void newOrder_shouldBePendingAndEmpty() {
        Order order = new Order(new User());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getTotal()).isEqualByComparingTo("0");
        assertThat(order.getTotalQuantity()).isZero();
    }

    @Test
    void addItem_shouldLinkItemToOrderAndUpdateTotal() {
        Order order = new Order(new User());

        OrderItem item = order.addItem(product(1L, "iPhone 15", "999.99"), 2);
        order.addItem(product(2L, "Case", "19.90"), 1);

        assertThat(item.getOrder()).isSameAs(order);
        assertThat(item.getSubtotal()).isEqualByComparingTo("1999.98");
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalQuantity()).isEqualTo(3);
        assertThat(order.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void addItem_shouldCopyNameAndPrice_soLaterProductChangesDoNotAffectTheOrder() {
        Product product = product(1L, "iPhone 15", "999.99");
        Order order = new Order(new User());
        OrderItem item = order.addItem(product, 1);

        product.setName("iPhone 15 (renamed)");
        product.setPrice(new BigDecimal("1299.00"));

        assertThat(item.getProductName()).isEqualTo("iPhone 15");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("999.99");
        assertThat(order.getTotal()).isEqualByComparingTo("999.99");
    }

    private Product product(Long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
