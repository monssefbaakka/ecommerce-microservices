package com.monssef.orderservice.cart;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the cart logic: no Spring context, no database
class CartTest {

    @Test
    void newCart_shouldBelongToEmailAndBeEmpty() {
        Cart cart = new Cart("alice@example.com");

        assertThat(cart.getUserEmail()).isEqualTo("alice@example.com");
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getProductIds()).isEmpty();
    }

    @Test
    void addItem_shouldLinkItemToCart() {
        Cart cart = new Cart("alice@example.com");

        CartItem item = cart.addItem(1L, 2);

        assertThat(cart.getItems()).containsExactly(item);
        assertThat(item.getCart()).isSameAs(cart);
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    void findItem_shouldMatchOnProductId() {
        Cart cart = new Cart("alice@example.com");
        cart.addItem(1L, 1);
        CartItem second = cart.addItem(2L, 1);

        assertThat(cart.findItem(2L)).containsSame(second);
        assertThat(cart.findItem(3L)).isEmpty();
    }

    @Test
    void getProductIds_shouldListLinesInOrder() {
        Cart cart = new Cart("alice@example.com");
        cart.addItem(2L, 1);
        cart.addItem(1L, 3);

        assertThat(cart.getProductIds()).containsExactly(2L, 1L);
    }
}
