package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.common.ResourceNotFoundException;
import com.monssef.paymentservice.order.Order;
import com.monssef.paymentservice.order.OrderRepository;
import com.monssef.paymentservice.order.OrderStatus;
import com.monssef.paymentservice.payment.dto.PaymentDTO;
import com.monssef.paymentservice.payment.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payments of the logged-in user. Every method takes the email from the JWT,
 * so a user can only ever pay or read the payments of their own orders.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CardProcessor cardProcessor;
    private final PaymentMapper mapper;

    // Charges the order total and records the attempt; a success sets the order to PAID, in the same
    // transaction. The order row is locked until the commit: a double click on "pay" runs the two
    // payments one after the other, and the second one finds the order already PAID (409)
    @Transactional
    public PaymentDTO pay(String email, PaymentRequest request) {
        Order order = orderRepository.findForUpdateByIdAndUserEmail(request.getOrderId(), email)
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.getOrderId()));
        if (!order.isPending()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order %d is %s, only a PENDING order can be paid".formatted(order.getId(), order.getStatus()));
        }
        PaymentStatus status = cardProcessor.charge(request.getCardNumber(), order.getTotal());
        if (status == PaymentStatus.SUCCEEDED) {
            order.setStatus(OrderStatus.PAID); // saved by dirty checking
        }
        Payment payment = paymentRepository.save(new Payment(order, request.getCardNumber(), status));
        return mapper.toDto(payment);
    }

    public Page<PaymentDTO> findAll(String email, Pageable pageable) {
        return paymentRepository.findByOrderUserEmail(email, pageable).map(mapper::toDto);
    }

    // 404 also when the payment exists but belongs to someone else: no way to probe other ids
    public PaymentDTO findById(String email, Long id) {
        return paymentRepository.findByIdAndOrderUserEmail(id, email)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }
}
