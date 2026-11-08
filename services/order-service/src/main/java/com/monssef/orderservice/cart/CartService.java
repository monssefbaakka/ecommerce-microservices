package com.monssef.orderservice.cart;

import com.monssef.orderservice.cart.dto.AddCartItemRequest;
import com.monssef.orderservice.cart.dto.CartDTO;
import com.monssef.orderservice.cart.dto.UpdateCartItemRequest;
import com.monssef.orderservice.common.ResourceNotFoundException;
import com.monssef.orderservice.product.InsufficientStockException;
import com.monssef.orderservice.product.ProductClient;
import com.monssef.orderservice.product.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart of the logged-in user. Every method takes the email from the JWT,
 * so a user can only ever read or change their own cart.
 * Names, prices and stock come from product-service at each request.
 * When product-service is unavailable, the cart is still shown, with the last known names and prices
 * (productsUpToDate false), but nothing that needs the stock can be done: adding or changing a quantity
 * gives a 503.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    // Per product line, on top of the stock of the product
    public static final int MAX_QUANTITY = 100;

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final CartMapper mapper;

    // No cart yet: return an empty one without writing anything on a GET
    public CartDTO getCart(String email) {
        return cartRepository.findByUserEmail(email)
                .map(this::toDto)
                .orElseGet(() -> new CartDTO(List.of(), 0, BigDecimal.ZERO));
    }

    // Adding a product already in the cart increases its quantity
    @Transactional
    public CartDTO addItem(String email, AddCartItemRequest request) {
        ProductInfo product = productClient.find(request.getProductId()); // 404 if unknown
        Cart cart = cartRepository.findByUserEmail(email)
                .orElseGet(() -> new Cart(email)); // the cart is created on the first add
        cart.findItem(product.id()).ifPresentOrElse(
                item -> item.setQuantity(checkQuantity(product, item.getQuantity() + request.getQuantity())),
                () -> cart.addItem(product.id(), checkQuantity(product, request.getQuantity())));
        return toDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDTO updateItem(String email, Long productId, UpdateCartItemRequest request) {
        CartItem item = findItem(email, productId);
        item.setQuantity(checkQuantity(productClient.find(productId), request.getQuantity()));
        return toDto(item.getCart());
    }

    @Transactional
    public CartDTO removeItem(String email, Long productId) {
        CartItem item = findItem(email, productId);
        Cart cart = item.getCart();
        cart.getItems().remove(item); // orphanRemoval deletes the row
        return toDto(cart);
    }

    @Transactional
    public void clear(String email) {
        cartRepository.findByUserEmail(email)
                .ifPresent(cart -> cart.getItems().clear());
    }

    // One call to product-service for all the lines of the cart, or the last known values if it is unavailable
    private CartDTO toDto(Cart cart) {
        return mapper.toDto(cart, productClient.findAllOrLastKnown(cart.getProductIds()));
    }

    private CartItem findItem(String email, Long productId) {
        return cartRepository.findByUserEmail(email)
                .flatMap(cart -> cart.findItem(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item for product", productId));
    }

    // 400 above the per-line maximum, 409 above the stock. product-service checks the stock again on order
    private int checkQuantity(ProductInfo product, int quantity) {
        if (quantity > MAX_QUANTITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quantity cannot exceed " + MAX_QUANTITY + " per product");
        }
        if (!product.hasStock(quantity)) {
            throw new InsufficientStockException(product);
        }
        return quantity;
    }
}
