package com.monssef.orderservice.order;

import com.monssef.orderservice.cart.Cart;
import com.monssef.orderservice.cart.CartRepository;
import com.monssef.orderservice.common.BusinessMetrics;
import com.monssef.orderservice.common.ResourceNotFoundException;
import com.monssef.orderservice.order.dto.OrderDTO;
import com.monssef.orderservice.order.dto.OrderSummaryDTO;
import com.monssef.orderservice.product.ProductClient;
import com.monssef.orderservice.product.ProductInfo;
import com.monssef.orderservice.product.ReservedItem;
import com.monssef.orderservice.product.StockLine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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
    private final BusinessMetrics metrics;

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
            metrics.orderPlaced(); // once the order is saved: a refused or failed order is not counted
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

    // For payment-service, which checks the owner itself (the call carries a SERVICE token, not the user's)
    public OrderSummaryDTO findSummary(Long id) {
        return orderRepository.findById(id)
                .map(mapper::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    // Called by payment-service once the card is accepted. The row is locked until the commit:
    // an order can only go from PENDING to PAID once (409 otherwise)
    @Transactional
    public OrderSummaryDTO markPaid(Long id) {
        Order order = orderRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order %d is %s, only a PENDING order can be paid".formatted(id, order.getStatus()));
        }
        order.setStatus(OrderStatus.PAID); // saved by dirty checking
        return mapper.toSummary(order);
    }

    // The user gives up an unpaid order: it becomes CANCELLED and its units go back to product-service.
    // Same row lock as markPaid: a payment and a cancellation of one order run one after the other,
    // and the second one gets a 409. 503 when product-service cannot take the stock back: nothing changes
    @Transactional
    public OrderDTO cancel(String email, Long id) {
        Order order = orderRepository.findForUpdateByIdAndUserEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order %d is %s, only a PENDING order can be cancelled".formatted(id, order.getStatus()));
        }
        cancelAndReleaseStock(order);
        metrics.orderCancelled(CancelReason.CUSTOMER);
        return mapper.toDto(order);
    }

    // For PendingOrderExpiry: the unpaid orders placed before the deadline, oldest first, at most max
    public List<Long> findExpiredIds(Instant deadline, int max) {
        return orderRepository.findIdsByStatusCreatedBefore(OrderStatus.PENDING, deadline, PageRequest.of(0, max));
    }

    // Cancels one order found by findExpiredIds, in its own transaction. Checked again under the lock:
    // paid or cancelled since it was listed, it is left alone (false)
    @Transactional
    public boolean expire(Long id, Instant deadline) {
        Order order = orderRepository.findForUpdateById(id).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING || !order.getCreatedAt().isBefore(deadline)) {
            return false;
        }
        cancelAndReleaseStock(order);
        metrics.orderCancelled(CancelReason.EXPIRED);
        return true;
    }

    // The new status is written first (the row is already locked, so this cannot fail on a conflict),
    // then product-service takes the units back, then the commit. If product-service refuses or is down,
    // the exception rolls the status back: the order stays PENDING with its stock, nothing is lost.
    // Left open, like after an order that could not be saved: a release that reached product-service
    // but whose answer was lost (timeout) rolls back here, and the units would be given back again on
    // the next try. An outbox would close it
    private void cancelAndReleaseStock(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.flush();
        productClient.release(order.getItems().stream()
                .map(item -> new StockLine(item.getProductId(), item.getQuantity()))
                .toList());
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
