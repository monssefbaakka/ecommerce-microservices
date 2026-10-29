package com.monssef.ecommerce.order.dto;

import com.monssef.ecommerce.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long id;
    private OrderStatus status;
    private List<OrderItemDTO> items;
    private int totalQuantity;
    private BigDecimal total;
    private Instant createdAt;
}
