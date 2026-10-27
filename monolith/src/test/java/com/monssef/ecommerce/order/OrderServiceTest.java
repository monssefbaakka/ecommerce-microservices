package com.monssef.ecommerce.order;

import com.monssef.ecommerce.cart.Cart;
import com.monssef.ecommerce.cart.CartRepository;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.order.dto.OrderDTO;
import com.monssef.ecommerce.product.Product;
import com.monssef.ecommerce.user.User;
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
class OrderServiceTest {

    private static final String EMAIL = "john@example.com";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private OrderService service;

    @Test
    void placeOrder_shouldCopyCartIntoPendingOrder() {
        User user = user();
        Cart cart = new Cart(user);
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        cart.addItem(product(2L, "Case", "19.90"), 1);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDTO result = service.placeOrder(EMAIL);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getProductId()).isEqualTo(1L);
        assertThat(result.getItems().get(0).getProductName()).isEqualTo("iPhone 15");
        assertThat(result.getItems().get(0).getUnitPrice()).isEqualByComparingTo("999.99");
        assertThat(result.getItems().get(0).getSubtotal()).isEqualByComparingTo("1999.98");
        assertThat(result.getTotalQuantity()).isEqualTo(3);
        assertThat(result.getTotal()).isEqualByComparingTo("2019.88");
    }

    @Test
    void placeOrder_shouldEmptyTheCart() {
        Cart cart = new Cart(user());
        cart.addItem(product(1L, "iPhone 15", "999.99"), 2);
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDTO result = service.placeOrder(EMAIL);

        assertThat(cart.getItems()).isEmpty();
        assertThat(result.getItems()).hasSize(1); // the order keeps its lines
    }

    @Test
    void placeOrder_shouldThrow400_whenCartIsEmpty() {
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(new Cart(user())));

        assertThatThrownBy(() -> service.placeOrder(EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_shouldThrow400_whenUserHasNoCart() {
        when(cartRepository.findByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cart is empty");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void findById_shouldReturnOrderOfUser() {
        Order order = new Order(user());
        order.setId(7L);
        order.addItem(product(1L, "iPhone 15", "999.99"), 1);
        when(orderRepository.findByIdAndUserEmail(7L, EMAIL)).thenReturn(Optional.of(order));

        OrderDTO result = service.findById(EMAIL, 7L);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getTotal()).isEqualByComparingTo("999.99");
    }

    @Test
    void findById_shouldReturnNullProductId_whenProductWasDeleted() {
        Order order = new Order(user());
        OrderItem item = order.addItem(product(1L, "iPhone 15", "999.99"), 1);
        item.setProduct(null); // what ON DELETE SET NULL leaves in the database
        when(orderRepository.findByIdAndUserEmail(7L, EMAIL)).thenReturn(Optional.of(order));

        OrderDTO result = service.findById(EMAIL, 7L);

        assertThat(result.getItems().get(0).getProductId()).isNull();
        assertThat(result.getItems().get(0).getProductName()).isEqualTo("iPhone 15");
    }

    @Test
    void findById_shouldThrow404_whenOrderMissingOrOfAnotherUser() {
        when(orderRepository.findByIdAndUserEmail(7L, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(EMAIL, 7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order with id 7 not found");
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
