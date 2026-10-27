package com.monssef.ecommerce.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain unit test of the stock logic: no Spring context, no database
class ProductTest {

    @Test
    void hasStock_shouldCompareWithUnitsLeft() {
        Product product = product(3);

        assertThat(product.hasStock(3)).isTrue();
        assertThat(product.hasStock(4)).isFalse();
    }

    @Test
    void removeStock_shouldDecreaseStock() {
        Product product = product(3);

        product.removeStock(3);

        assertThat(product.getStock()).isZero();
    }

    @Test
    void removeStock_shouldThrowAndKeepStock_whenNotEnoughLeft() {
        Product product = product(3);

        assertThatThrownBy(() -> product.removeStock(4))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getStock()).isEqualTo(3);
    }

    private Product product(int stock) {
        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone 15");
        product.setStock(stock);
        return product;
    }
}
