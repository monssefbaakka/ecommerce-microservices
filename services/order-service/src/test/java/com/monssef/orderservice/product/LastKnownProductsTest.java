package com.monssef.orderservice.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LastKnownProductsTest {

    private static final ProductInfo IPHONE = new ProductInfo(1L, "iPhone 15", new BigDecimal("999.99"), 10);
    private static final ProductInfo CASE = new ProductInfo(2L, "Case", new BigDecimal("19.90"), 3);

    private final LastKnownProducts lastKnown = new LastKnownProducts();

    @Test
    void get_shouldReturnTheProductsOfTheLastAnswer() {
        lastKnown.remember(List.of(1L, 2L), List.of(IPHONE, CASE));

        assertThat(lastKnown.get(List.of(1L, 2L))).containsOnlyKeys(1L, 2L).containsEntry(1L, IPHONE);
    }

    @Test
    void get_shouldLeaveOutProductsNeverSeen() {
        lastKnown.remember(List.of(1L), List.of(IPHONE));

        assertThat(lastKnown.get(List.of(1L, 99L))).containsOnlyKeys(1L);
    }

    @Test
    void remember_shouldReplaceAnOlderAnswer() {
        lastKnown.remember(List.of(1L), List.of(IPHONE));
        ProductInfo cheaper = new ProductInfo(1L, "iPhone 15", new BigDecimal("899.99"), 4);

        lastKnown.remember(List.of(1L), List.of(cheaper));

        assertThat(lastKnown.get(List.of(1L))).containsEntry(1L, cheaper);
    }

    @Test
    void remember_shouldForgetAProductAskedForButMissingFromTheAnswer() {
        lastKnown.remember(List.of(1L, 2L), List.of(IPHONE, CASE));

        lastKnown.remember(List.of(2L), List.of());   // deleted in product-service since

        assertThat(lastKnown.get(List.of(1L, 2L))).containsOnlyKeys(1L);
    }
}
