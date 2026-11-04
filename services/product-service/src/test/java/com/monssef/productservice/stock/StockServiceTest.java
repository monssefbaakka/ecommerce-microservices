package com.monssef.productservice.stock;

import com.monssef.productservice.common.ResourceNotFoundException;
import com.monssef.productservice.product.InsufficientStockException;
import com.monssef.productservice.product.Product;
import com.monssef.productservice.product.ProductRepository;
import com.monssef.productservice.stock.dto.ReservedItem;
import com.monssef.productservice.stock.dto.StockLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // plain unit test: no Spring context, no database
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private StockService service;

    @Test
    void reserve_shouldRemoveStockAndReturnNameAndPrice() {
        Product phone = product(1L, "iPhone 15", "999.99", 5);
        Product phoneCase = product(2L, "Case", "19.90", 50);
        when(productRepository.findAllForUpdate(Set.of(2L, 1L))).thenReturn(List.of(phone, phoneCase));

        List<ReservedItem> result = service.reserve(List.of(new StockLine(2L, 1), new StockLine(1L, 2)));

        assertThat(phone.getStock()).isEqualTo(3);
        assertThat(phoneCase.getStock()).isEqualTo(49);
        // same order as the request
        assertThat(result).containsExactly(
                new ReservedItem(2L, "Case", new BigDecimal("19.90"), 1),
                new ReservedItem(1L, "iPhone 15", new BigDecimal("999.99"), 2));
    }

    @Test
    void reserve_shouldSumTheLinesOfTheSameProduct() {
        Product phone = product(1L, "iPhone 15", "999.99", 5);
        when(productRepository.findAllForUpdate(Set.of(1L))).thenReturn(List.of(phone));

        List<ReservedItem> result = service.reserve(List.of(new StockLine(1L, 2), new StockLine(1L, 3)));

        assertThat(phone.getStock()).isZero();
        assertThat(result).singleElement().extracting(ReservedItem::getQuantity).isEqualTo(5);
    }

    @Test
    void reserve_shouldThrow409_whenOneProductLacksStock() {
        Product phone = product(1L, "iPhone 15", "999.99", 1);
        Product phoneCase = product(2L, "Case", "19.90", 50);
        when(productRepository.findAllForUpdate(Set.of(2L, 1L))).thenReturn(List.of(phone, phoneCase));

        // the case line passes first: the transaction rollback gives its unit back in the database
        assertThatThrownBy(() -> service.reserve(List.of(new StockLine(2L, 1), new StockLine(1L, 2))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Not enough stock for product 1 (iPhone 15): 1 left");

        assertThat(phone.getStock()).isEqualTo(1);
    }

    @Test
    void reserve_shouldThrow404_whenProductUnknown() {
        when(productRepository.findAllForUpdate(Set.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.reserve(List.of(new StockLine(99L, 1))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void release_shouldGiveUnitsBackAndSkipDeletedProducts() {
        Product phone = product(1L, "iPhone 15", "999.99", 3);
        when(productRepository.findAllForUpdate(Set.of(1L, 99L))).thenReturn(List.of(phone));

        service.release(List.of(new StockLine(1L, 2), new StockLine(99L, 1)));

        assertThat(phone.getStock()).isEqualTo(5);
    }

    private Product product(Long id, String name, String price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        return product;
    }
}
