package com.monssef.orderservice.cart;

import com.monssef.orderservice.cart.dto.CartDTO;
import com.monssef.orderservice.cart.dto.CartItemDTO;
import com.monssef.orderservice.product.ProductInfo;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the generated mapper: no Spring context, no database
class CartMapperTest {

    private final CartMapper mapper = Mappers.getMapper(CartMapper.class);

    @Test
    void toDto_shouldMapItemsAndComputedTotals() {
        Cart cart = new Cart("alice@example.com");
        cart.addItem(1L, 2);
        cart.addItem(2L, 1);

        CartDTO dto = mapper.toDto(cart, Map.of(
                1L, product(1L, "iPhone 15", "999.99"),
                2L, product(2L, "Case", "19.90")));

        assertThat(dto.getItems()).extracting(CartItemDTO::getProductId).containsExactly(1L, 2L);
        assertThat(dto.getTotalQuantity()).isEqualTo(3);
        assertThat(dto.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void toDto_shouldUseNameAndPriceFromProductService() {
        CartItem item = new Cart("alice@example.com").addItem(1L, 2);

        CartItemDTO dto = mapper.toDto(item, product(1L, "iPhone 15 Pro", "1099.99"));

        assertThat(dto.getProductId()).isEqualTo(1L);
        assertThat(dto.getProductName()).isEqualTo("iPhone 15 Pro");
        assertThat(dto.getUnitPrice()).isEqualByComparingTo("1099.99");
        assertThat(dto.getQuantity()).isEqualTo(2);
        assertThat(dto.getSubtotal()).isEqualByComparingTo("2199.98");
    }

    @Test
    void toDto_shouldLeaveOutLines_whoseProductWasDeleted() {
        Cart cart = new Cart("alice@example.com");
        cart.addItem(1L, 2);
        cart.addItem(2L, 1);

        CartDTO dto = mapper.toDto(cart, Map.of(2L, product(2L, "Case", "19.90")));

        assertThat(dto.getItems()).extracting(CartItemDTO::getProductId).containsExactly(2L);
        assertThat(dto.getTotalQuantity()).isEqualTo(1);
        assertThat(dto.getTotal()).isEqualByComparingTo("19.90");
    }

    @Test
    void toDto_shouldMapEmptyCart() {
        CartDTO dto = mapper.toDto(new Cart("alice@example.com"), Map.of());

        assertThat(dto.getItems()).isEmpty();
        assertThat(dto.getTotalQuantity()).isZero();
        assertThat(dto.getTotal()).isEqualByComparingTo("0");
    }

    private ProductInfo product(Long id, String name, String price) {
        return new ProductInfo(id, name, new BigDecimal(price), 10);
    }
}
