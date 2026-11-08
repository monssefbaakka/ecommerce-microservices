package com.monssef.orderservice.cart;

import com.monssef.orderservice.cart.dto.CartDTO;
import com.monssef.orderservice.cart.dto.CartItemDTO;
import com.monssef.orderservice.product.ProductInfo;
import com.monssef.orderservice.product.ProductSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface CartMapper {

    // The current product name and price, from product-service: the cart keeps no copy, unlike the order
    @Mapping(target = "productId", source = "item.productId")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unitPrice", source = "product.price")
    @Mapping(target = "quantity", source = "item.quantity")
    @Mapping(target = "subtotal", expression = "java(product.price().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    CartItemDTO toDto(CartItem item, ProductInfo product);

    // Lines whose product is missing from the map (deleted in product-service) are left out
    default CartDTO toDto(Cart cart, Map<Long, ProductInfo> products) {
        List<CartItemDTO> items = cart.getItems().stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> toDto(item, products.get(item.getProductId())))
                .toList();
        int totalQuantity = items.stream().mapToInt(CartItemDTO::getQuantity).sum();
        BigDecimal total = items.stream().map(CartItemDTO::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartDTO(items, totalQuantity, total);
    }

    /**
     * Up to date: as above, deleted products left out.
     * Last known values (product-service unavailable): a product missing from the map was never seen,
     * not deleted, so its line stays, with its quantity but no name nor price, and the total is unknown.
     */
    default CartDTO toDto(Cart cart, ProductSnapshot snapshot) {
        if (snapshot.upToDate()) {
            return toDto(cart, snapshot.products());
        }
        Map<Long, ProductInfo> products = snapshot.products();
        List<CartItemDTO> items = cart.getItems().stream()
                .map(item -> products.containsKey(item.getProductId())
                        ? toDto(item, products.get(item.getProductId()))
                        : new CartItemDTO(item.getProductId(), null, null, item.getQuantity(), null))
                .toList();
        int totalQuantity = items.stream().mapToInt(CartItemDTO::getQuantity).sum();
        BigDecimal total = items.stream().anyMatch(item -> item.getSubtotal() == null) ? null
                : items.stream().map(CartItemDTO::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartDTO(items, totalQuantity, total, false);
    }
}
