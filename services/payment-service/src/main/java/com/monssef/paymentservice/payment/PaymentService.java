package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.common.BusinessMetrics;
import com.monssef.paymentservice.common.ResourceNotFoundException;
import com.monssef.paymentservice.order.OrderClient;
import com.monssef.paymentservice.order.OrderSummary;
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

    private final OrderClient orderClient;
    private final OrderPaymentLock orderPaymentLock;
    private final PaymentRepository paymentRepository;
    private final CardProcessor cardProcessor;
    private final PaymentMapper mapper;
    private final BusinessMetrics metrics;

    // Charges the order total and records the attempt; a success sets the order to PAID in order-service.
    // The advisory lock on the order id is held until the commit: a double click on "pay" runs the two
    // payments one after the other, and the second one finds the order already PAID (409)
    @Transactional
    public PaymentDTO pay(String email, PaymentRequest request) {
        Long orderId = request.getOrderId();
        orderPaymentLock.lock(orderId);
        OrderSummary order = orderClient.find(orderId); // 404 if unknown, 503 if order-service is down
        if (!order.belongsTo(email)) {
            throw new ResourceNotFoundException("Order", orderId); // someone else's order looks unknown
        }
        if (!order.isPending()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order %d is %s, only a PENDING order can be paid".formatted(orderId, order.status()));
        }
        PaymentStatus status = cardProcessor.charge(request.getCardNumber(), order.total());
        if (status == PaymentStatus.SUCCEEDED) {
            // 409 if the order changed meanwhile: nothing is saved here. The card is simulated,
            // a real one would have to be refunded
            orderClient.markPaid(orderId);
        }
        Payment payment = paymentRepository.save(new Payment(order, request.getCardNumber(), status));
        metrics.paymentRecorded(status); // only once recorded: a refused "mark paid" (409) is not counted
        return mapper.toDto(payment);
    }

    public Page<PaymentDTO> findAll(String email, Pageable pageable) {
        return paymentRepository.findByUserEmail(email, pageable).map(mapper::toDto);
    }

    // 404 also when the payment exists but belongs to someone else: no way to probe other ids
    public PaymentDTO findById(String email, Long id) {
        return paymentRepository.findByIdAndUserEmail(id, email)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }
}
