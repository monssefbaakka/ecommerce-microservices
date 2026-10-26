package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.cart.dto.AddCartItemRequest;
import com.monssef.ecommerce.cart.dto.CartDTO;
import com.monssef.ecommerce.cart.dto.CartItemDTO;
import com.monssef.ecommerce.cart.dto.UpdateCartItemRequest;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.product.Product;
import com.monssef.ecommerce.product.ProductService;
import com.monssef.ecommerce.user.User;
import com.monssef.ecommerce.user.UserRepository;
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    // Per product line. Real stock checks come with stock management (#31)
    public static final int MAX_QUANTITY = 100;

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    // No cart yet: return an empty one without writing anything on a GET
    public CartDTO getCart(String email) {
        return cartRepository.findByUserEmail(email)
                .map(this::toDto)
                .orElseGet(() -> new CartDTO(List.of(), 0, BigDecimal.ZERO));
    }

    // Adding a product already in the cart increases its quantity
    @Transactional
    public CartDTO addItem(String email, AddCartItemRequest request) {
        Product product = productService.findEntityById(request.getProductId()); // 404 if unknown
        Cart cart = cartRepository.findByUserEmail(email)
                .orElseGet(() -> new Cart(findUser(email))); // the cart is created on the first add
        cart.findItem(product.getId()).ifPresentOrElse(
                item -> item.setQuantity(checkQuantity(item.getQuantity() + request.getQuantity())),
                () -> cart.addItem(product, request.getQuantity()));
        return toDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDTO updateItem(String email, Long productId, UpdateCartItemRequest request) {
        CartItem item = findItem(email, productId);
        item.setQuantity(request.getQuantity());
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

    private CartItem findItem(String email, Long productId) {
        return cartRepository.findByUserEmail(email)
                .flatMap(cart -> cart.findItem(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item for product", productId));
    }

    // The token is valid but the account may have been deleted since it was issued
    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private int checkQuantity(int quantity) {
        if (quantity > MAX_QUANTITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quantity cannot exceed " + MAX_QUANTITY + " per product");
        }
        return quantity;
    }

    private CartDTO toDto(Cart cart) {
        List<CartItemDTO> items = cart.getItems().stream()
                .map(this::toDto)
                .toList();
        int totalQuantity = items.stream().mapToInt(CartItemDTO::getQuantity).sum();
        BigDecimal total = items.stream()
                .map(CartItemDTO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartDTO(items, totalQuantity, total);
    }

    private CartItemDTO toDto(CartItem item) {
        Product product = item.getProduct();
        return new CartItemDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
