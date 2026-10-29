package com.monssef.ecommerce.order;

import com.monssef.ecommerce.cart.Cart;
import com.monssef.ecommerce.cart.CartItem;
import com.monssef.ecommerce.cart.CartRepository;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.order.dto.OrderDTO;
import com.monssef.ecommerce.order.dto.OrderItemDTO;
import com.monssef.ecommerce.product.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;

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
    private final EntityManager entityManager;

    // Turns the cart into an order, removes the units from the stock, then empties the cart,
    // in one transaction: if one product lacks stock (409), nothing is changed.
    // The cart and its products are locked until the commit, so two orders at the same time
    // run one after the other and can never both take the last unit
    @Transactional
    public OrderDTO placeOrder(String email) {
        Cart cart = cartRepository.findForUpdateByUserEmail(email)
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty"));
        lockProducts(cart);
        Order order = new Order(cart.getUser());
        cart.getItems().forEach(item -> {
            item.getProduct().removeStock(item.getQuantity()); // saved by dirty checking
            order.addItem(item.getProduct(), item.getQuantity());
        });
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

    // Reloads each product with a row lock (SELECT ... FOR UPDATE): an order of the same product
    // waits here, then reads the stock left by the first one instead of a stale value.
    // Always locked in id order, so two orders never wait on each other forever (deadlock)
    private void lockProducts(Cart cart) {
        cart.getItems().stream()
                .map(CartItem::getProduct)
                .sorted(Comparator.comparing(Product::getId))
                .forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE));
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
