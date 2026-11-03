package com.monssef.paymentservice.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the simulated card network
class CardProcessorTest {

    private final CardProcessor processor = new CardProcessor();

    @Test
    void charge_shouldSucceed_forAnyOtherCard() {
        assertThat(processor.charge("4242424242424242", new BigDecimal("1999.98")))
                .isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void charge_shouldDecline_whenCardEndsWith0002() {
        assertThat(processor.charge("4000000000000002", new BigDecimal("1999.98")))
                .isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void charge_shouldLookOnlyAtTheEndOfTheNumber() {
        assertThat(processor.charge("0002424242424242", BigDecimal.ONE))
                .isEqualTo(PaymentStatus.SUCCEEDED);
    }
}
