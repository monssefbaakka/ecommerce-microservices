package com.monssef.orderservice.order;

import com.monssef.orderservice.cart.Cart;
import com.monssef.orderservice.cart.CartRepository;
import com.monssef.orderservice.common.ResourceNotFoundException;
import com.monssef.orderservice.order.dto.OrderDTO;
import com.monssef.orderservice.product.ProductClient;
import com.monssef.orderservice.product.ProductInfo;
import com.monssef.orderservice.product.ReservedItem;
import com.monssef.orderservice.product.StockLine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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
    private final ProductClient productClient;
    private final OrderMapper mapper;

    // Turns the cart into an order and empties the cart. product-service removes the units from the stock
    // (all lines or none: 409 if one lacks stock) and gives the name and price to copy into the order.
    // The cart stays locked until the commit, so a double click on "order" places only one order
    @Transactional
    public OrderDTO placeOrder(String email) {
        Cart cart = cartRepository.findForUpdateByUserEmail(email)
                .orElseThrow(OrderService::emptyCart);
        dropDeletedProducts(cart);
        if (cart.getItems().isEmpty()) {
            throw emptyCart();
        }
        List<StockLine> lines = cart.getItems().stream()
                .map(item -> new StockLine(item.getProductId(), item.getQuantity()))
                .toList();
        List<ReservedItem> reserved = productClient.reserve(lines); // 404 / 409 / 503: nothing changed
        try {
            Order order = new Order(email);
            reserved.forEach(order::addItem);
            Order saved = orderRepository.saveAndFlush(order);
            cart.getItems().clear(); // orphanRemoval deletes the cart lines
            return mapper.toDto(saved);
        } catch (RuntimeException e) {
            // The stock is already taken in product-service: give it back, the order does not exist
            productClient.release(lines);
            throw e;
        }
    }

    public Page<OrderDTO> findAll(String email, Pageable pageable) {
        return orderRepository.findByUserEmail(email, pageable).map(mapper::toDto);
    }

    // 404 also when the order exists but belongs to someone else: no way to probe other ids
    public OrderDTO findById(String email, Long id) {
        return orderRepository.findByIdAndUserEmail(id, email)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    // A product deleted in product-service since it was added simply leaves the cart, as the cart shows it
    private void dropDeletedProducts(Cart cart) {
        Map<Long, ProductInfo> products = productClient.findAll(cart.getProductIds());
        cart.getItems().removeIf(item -> !products.containsKey(item.getProductId()));
    }

    private static ResponseStatusException emptyCart() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
    }
}
