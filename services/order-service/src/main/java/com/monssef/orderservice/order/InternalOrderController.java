package com.monssef.orderservice.order;

import com.monssef.orderservice.order.dto.OrderSummaryDTO;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// For payment-service only (role SERVICE): not routed by the gateway, hidden from Swagger UI
@Hidden
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public OrderSummaryDTO findById(@PathVariable Long id) {
        return orderService.findSummary(id);
    }

    // 200 with the order now PAID, 404 unknown order, 409 not PENDING any more
    @PostMapping("/{id}/paid")
    public OrderSummaryDTO markPaid(@PathVariable Long id) {
        return orderService.markPaid(id);
    }
}
