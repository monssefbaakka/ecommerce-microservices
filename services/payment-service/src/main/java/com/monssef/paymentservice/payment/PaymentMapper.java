package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.payment.dto.PaymentDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentDTO toDto(Payment payment);
}
