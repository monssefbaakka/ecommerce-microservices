package com.monssef.ecommerce.order;

import com.monssef.ecommerce.order.dto.OrderDTO;
import com.monssef.ecommerce.order.dto.OrderItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderDTO toDto(Order order);

    // productId is null when the product was deleted since: MapStruct checks product for null
    @Mapping(target = "productId", source = "product.id")
    OrderItemDTO toDto(OrderItem item);
}
