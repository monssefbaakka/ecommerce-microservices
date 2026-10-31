package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.product.Product;
import com.monssef.ecommerce.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the cart logic: no Spring context, no database
class CartTest {

    @Test
    void addItem_shouldLinkItemToCart() {
        Cart cart = new Cart(new User());

        CartItem item = cart.addItem(product(1L, "999.99"), 2);

        assertThat(cart.getItems()).containsExactly(item);
        assertThat(item.getCart()).isSameAs(cart);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    void findItem_shouldMatchOnProductId() {
        Cart cart = new Cart(new User());
        cart.addItem(product(1L, "999.99"), 1);
        CartItem second = cart.addItem(product(2L, "19.90"), 1);

        assertThat(cart.findItem(2L)).containsSame(second);
        assertThat(cart.findItem(3L)).isEmpty();
    }

    @Test
    void totals_shouldSumEveryLine() {
        Cart cart = new Cart(new User());
        cart.addItem(product(1L, "999.99"), 2);
        cart.addItem(product(2L, "19.90"), 3);

        assertThat(cart.getTotalQuantity()).isEqualTo(5);
        assertThat(cart.getTotal()).isEqualByComparingTo("2059.68");
    }

    @Test
    void total_shouldFollowCurrentProductPrice() {
        Product product = product(1L, "999.99");
        Cart cart = new Cart(new User());
        cart.addItem(product, 2);

        product.setPrice(new BigDecimal("899.99"));

        assertThat(cart.getTotal()).isEqualByComparingTo("1799.98");
    }

    @Test
    void totals_shouldBeZero_whenEmpty() {
        Cart cart = new Cart(new User());

        assertThat(cart.getTotalQuantity()).isZero();
        assertThat(cart.getTotal()).isEqualByComparingTo("0");
    }

    private Product product(Long id, String price) {
        Product product = new Product();
        product.setId(id);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
