package com.monssef.ecommerce.product;

import com.monssef.ecommerce.category.Category;
import com.monssef.ecommerce.product.dto.ProductDTO;
import com.monssef.ecommerce.product.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the generated mapper: no Spring context, no database
class ProductMapperTest {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void toDto_shouldExposeCategoryIdOnly() {
        Product product = product();

        ProductDTO dto = mapper.toDto(product);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("iPhone 15");
        assertThat(dto.getDescription()).isEqualTo("128 GB");
        assertThat(dto.getPrice()).isEqualByComparingTo("999.99");
        assertThat(dto.getCategoryId()).isEqualTo(5L);
        assertThat(dto.getStock()).isEqualTo(10);
        assertThat(dto.getCreatedAt()).isEqualTo(product.getCreatedAt());
    }

    @Test
    void toEntity_shouldCopyFieldsAndLeaveIdAndCategoryEmpty() {
        Product product = mapper.toEntity(request());

        assertThat(product.getId()).isNull();
        assertThat(product.getName()).isEqualTo("iPhone 16");
        assertThat(product.getDescription()).isEqualTo("256 GB");
        assertThat(product.getPrice()).isEqualByComparingTo("1199.99");
        assertThat(product.getStock()).isEqualTo(3);
        assertThat(product.getCategory()).isNull(); // set by ProductService from categoryId
    }

    @Test
    void update_shouldKeepIdCategoryAndCreationDate() {
        Product product = product();
        Category category = product.getCategory();
        Instant createdAt = product.getCreatedAt();

        mapper.update(request(), product);

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("iPhone 16");
        assertThat(product.getPrice()).isEqualByComparingTo("1199.99");
        assertThat(product.getStock()).isEqualTo(3);
        assertThat(product.getCategory()).isSameAs(category);
        assertThat(product.getCreatedAt()).isEqualTo(createdAt);
    }

    private Product product() {
        Category category = new Category();
        category.setId(5L);
        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone 15");
        product.setDescription("128 GB");
        product.setPrice(new BigDecimal("999.99"));
        product.setCategory(category);
        product.setStock(10);
        product.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return product;
    }

    private ProductRequest request() {
        return new ProductRequest("iPhone 16", "256 GB", new BigDecimal("1199.99"), 9L, 3);
    }
}
