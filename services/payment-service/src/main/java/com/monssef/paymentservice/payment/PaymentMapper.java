package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.payment.dto.PaymentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "orderId", source = "order.id")
    PaymentDTO toDto(Payment payment);
}
