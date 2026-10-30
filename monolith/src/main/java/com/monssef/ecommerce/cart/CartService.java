package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.cart.dto.AddCartItemRequest;
import com.monssef.ecommerce.cart.dto.CartDTO;
import com.monssef.ecommerce.cart.dto.UpdateCartItemRequest;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.product.InsufficientStockException;
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

    // Per product line, on top of the stock of the product
    public static final int MAX_QUANTITY = 100;

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final CartMapper mapper;

    // No cart yet: return an empty one without writing anything on a GET
    public CartDTO getCart(String email) {
        return cartRepository.findByUserEmail(email)
                .map(mapper::toDto)
                .orElseGet(() -> new CartDTO(List.of(), 0, BigDecimal.ZERO));
    }

    // Adding a product already in the cart increases its quantity
    @Transactional
    public CartDTO addItem(String email, AddCartItemRequest request) {
        Product product = productService.findEntityById(request.getProductId()); // 404 if unknown
        Cart cart = cartRepository.findByUserEmail(email)
                .orElseGet(() -> new Cart(findUser(email))); // the cart is created on the first add
        cart.findItem(product.getId()).ifPresentOrElse(
                item -> item.setQuantity(checkQuantity(product, item.getQuantity() + request.getQuantity())),
                () -> cart.addItem(product, checkQuantity(product, request.getQuantity())));
        return mapper.toDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDTO updateItem(String email, Long productId, UpdateCartItemRequest request) {
        CartItem item = findItem(email, productId);
        item.setQuantity(checkQuantity(item.getProduct(), request.getQuantity()));
        return mapper.toDto(item.getCart());
    }

    @Transactional
    public CartDTO removeItem(String email, Long productId) {
        CartItem item = findItem(email, productId);
        Cart cart = item.getCart();
        cart.getItems().remove(item); // orphanRemoval deletes the row
        return mapper.toDto(cart);
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

    // 400 above the per-line maximum, 409 above the stock. The order checks the stock again
    private int checkQuantity(Product product, int quantity) {
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
