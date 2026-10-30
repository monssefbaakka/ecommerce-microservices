package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.cart.dto.CartDTO;
import com.monssef.ecommerce.cart.dto.CartItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CartMapper {

    CartDTO toDto(Cart cart);

    // The current product name and price: the cart keeps no copy, unlike the order
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unitPrice", source = "product.price")
    CartItemDTO toDto(CartItem item);
}
