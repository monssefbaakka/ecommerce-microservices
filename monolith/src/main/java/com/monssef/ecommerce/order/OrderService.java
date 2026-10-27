package com.monssef.ecommerce.order;

import com.monssef.ecommerce.cart.Cart;
import com.monssef.ecommerce.cart.CartRepository;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.order.dto.OrderDTO;
import com.monssef.ecommerce.order.dto.OrderItemDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orders of the logged-in user. Like the cart, every method takes the email
 * from the JWT, so a user can only ever place or read their own orders.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;

    // Turns the cart into an order, then empties the cart, in one transaction:
    // if saving the order fails, the cart is left untouched.
    // Stock is not checked yet: that comes with #31 and #32
    @Transactional
    public OrderDTO placeOrder(String email) {
        Cart cart = cartRepository.findByUserEmail(email)
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty"));
        Order order = new Order(cart.getUser());
        cart.getItems().forEach(item -> order.addItem(item.getProduct(), item.getQuantity()));
        Order saved = orderRepository.save(order);
        cart.getItems().clear(); // orphanRemoval deletes the cart lines
        return toDto(saved);
    }

    // 404 also when the order exists but belongs to someone else: no way to probe other ids
    public OrderDTO findById(String email, Long id) {
        return orderRepository.findByIdAndUserEmail(id, email)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private OrderDTO toDto(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getStatus(),
                order.getItems().stream().map(this::toDto).toList(),
                order.getTotalQuantity(),
                order.getTotal(),
                order.getCreatedAt()
        );
    }

    private OrderItemDTO toDto(OrderItem item) {
        return new OrderItemDTO(
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
