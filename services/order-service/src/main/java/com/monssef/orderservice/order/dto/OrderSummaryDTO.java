package com.monssef.orderservice.order.dto;

import com.monssef.orderservice.order.OrderStatus;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// What payment-service needs to pay an order: its owner, its status and the total to charge
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {

    private Long id;
    private String userEmail;
    private OrderStatus status;
    private BigDecimal total;
}
