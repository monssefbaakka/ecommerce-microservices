package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.cart.dto.CartDTO;
import com.monssef.ecommerce.cart.dto.CartItemDTO;
import com.monssef.ecommerce.product.Product;
import com.monssef.ecommerce.user.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the generated mapper: no Spring context, no database
class CartMapperTest {

    private final CartMapper mapper = Mappers.getMapper(CartMapper.class);

    @Test
    void toDto_shouldMapItemsAndComputedTotals() {
        Cart cart = new Cart(new User());
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        cart.addItem(product(2L, "Case", "19.90"), 1);

        CartDTO dto = mapper.toDto(cart);

        assertThat(dto.getItems()).extracting(CartItemDTO::getProductId).containsExactly(1L, 2L);
        assertThat(dto.getTotalQuantity()).isEqualTo(3);
        assertThat(dto.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void toDto_shouldUseCurrentProductNameAndPrice() {
        Product product = product(1L, "iPhone 15", "999.99");
        CartItem item = new Cart(new User()).addItem(product, 2);
        product.setName("iPhone 15 Pro");
        product.setPrice(new BigDecimal("1099.99"));

        CartItemDTO dto = mapper.toDto(item);

        assertThat(dto.getProductName()).isEqualTo("iPhone 15 Pro");
        assertThat(dto.getUnitPrice()).isEqualByComparingTo("1099.99");
        assertThat(dto.getQuantity()).isEqualTo(2);
        assertThat(dto.getSubtotal()).isEqualByComparingTo("2199.98");
    }

    @Test
    void toDto_shouldMapEmptyCart() {
        CartDTO dto = mapper.toDto(new Cart(new User()));

        assertThat(dto.getItems()).isEmpty();
        assertThat(dto.getTotalQuantity()).isZero();
        assertThat(dto.getTotal()).isEqualByComparingTo("0");
    }

    private Product product(Long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
