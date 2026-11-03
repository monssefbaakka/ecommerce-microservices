package com.monssef.paymentservice.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Simulated card network: no real bank is called and no money moves.
 * Like the test cards of real payment providers, the card number decides the answer:
 * a number ending in 0002 is declined, any other one is accepted.
 * A real provider would replace this class without changing PaymentService.
 */
@Component
public class CardProcessor {

    static final String DECLINED_SUFFIX = "0002";

    public PaymentStatus charge(String cardNumber, BigDecimal amount) {
        return cardNumber.endsWith(DECLINED_SUFFIX) ? PaymentStatus.DECLINED : PaymentStatus.SUCCEEDED;
    }
}
