package com.monssef.orderservice.order;

import com.monssef.orderservice.order.dto.OrderDTO;
import com.monssef.orderservice.order.dto.OrderItemDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderDTO toDto(Order order);

    OrderItemDTO toDto(OrderItem item);
}
