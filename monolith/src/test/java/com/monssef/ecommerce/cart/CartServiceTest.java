package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.cart.dto.AddCartItemRequest;
import com.monssef.ecommerce.cart.dto.CartDTO;
import com.monssef.ecommerce.cart.dto.UpdateCartItemRequest;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.product.Product;
import com.monssef.ecommerce.product.ProductService;
import com.monssef.ecommerce.user.User;
import com.monssef.ecommerce.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // plain unit test: no Spring context, no database
class CartServiceTest {

    private static final String EMAIL = "john@example.com";

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private CartService service;

    @Test
    void getCart_shouldReturnEmptyCart_whenUserHasNone() {
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.empty());

        CartDTO result = service.getCart(EMAIL);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalQuantity()).isZero();
        assertThat(result.getTotal()).isEqualByComparingTo("0");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void getCart_shouldComputeSubtotalsAndTotal() {
        Cart cart = new Cart(user());
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        cart.addItem(product(2L, "Case", "19.90"), 1);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));

        CartDTO result = service.getCart(EMAIL);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getProductName()).isEqualTo("iPhone 15");
        assertThat(result.getItems().get(0).getSubtotal()).isEqualByComparingTo("1999.98");
        assertThat(result.getTotalQuantity()).isEqualTo(3);
        assertThat(result.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void addItem_shouldCreateCart_whenUserHasNone() {
        when(productService.findEntityById(1L)).thenReturn(product(1L, "iPhone 15", "999.99"));
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = service.addItem(EMAIL, new AddCartItemRequest(1L, 2));

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getEmail()).isEqualTo(EMAIL);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualByComparingTo("1999.98");
    }

    @Test
    void addItem_shouldIncreaseQuantity_whenProductAlreadyInCart() {
        Product product = product(1L, "iPhone 15", "999.99");
        Cart cart = new Cart(user());
        cart.addItem(product, 2);
        when(productService.findEntityById(1L)).thenReturn(product);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);

        CartDTO result = service.addItem(EMAIL, new AddCartItemRequest(1L, 3));

        assertThat(result.getItems()).hasSize(1); // same line, not a second one
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void addItem_shouldReturn400_whenTotalQuantityExceedsMax() {
        Product product = product(1L, "iPhone 15", "999.99");
        Cart cart = new Cart(user());
        cart.addItem(product, CartService.MAX_QUANTITY);
        when(productService.findEntityById(1L)).thenReturn(product);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.addItem(EMAIL, new AddCartItemRequest(1L, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_shouldThrow404_whenProductDoesNotExist() {
        when(productService.findEntityById(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));

        assertThatThrownBy(() -> service.addItem(EMAIL, new AddCartItemRequest(99L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cartRepository);
    }

    @Test
    void updateItem_shouldReplaceQuantity() {
        Cart cart = new Cart(user());
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));

        CartDTO result = service.updateItem(EMAIL, 1L, new UpdateCartItemRequest(7));

        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(7);
    }

    @Test
    void updateItem_shouldThrow404_whenProductNotInCart() {
        Cart cart = new Cart(user());
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.updateItem(EMAIL, 2L, new UpdateCartItemRequest(1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItem_shouldRemoveOnlyThatProduct() {
        Cart cart = new Cart(user());
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        cart.addItem(product(2L, "Case", "19.90"), 1);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));

        CartDTO result = service.removeItem(EMAIL, 1L);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProductId()).isEqualTo(2L);
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void removeItem_shouldThrow404_whenUserHasNoCart() {
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(EMAIL, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clear_shouldRemoveAllItems() {
        Cart cart = new Cart(user());
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));

        service.clear(EMAIL);

        assertThat(cart.getItems()).isEmpty();
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);
        return user;
    }

    private Product product(Long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
