package com.monssef.ecommerce.category;

import com.monssef.ecommerce.category.dto.CategoryDTO;
import com.monssef.ecommerce.category.dto.CategoryRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the generated mapper: no Spring context, no database
class CategoryMapperTest {

    private final CategoryMapper mapper = Mappers.getMapper(CategoryMapper.class);

    @Test
    void toDto_shouldCopyAllFields() {
        CategoryDTO dto = mapper.toDto(category());

        assertThat(dto).isEqualTo(new CategoryDTO(1L, "Electronics", "Phones and laptops"));
    }

    @Test
    void toEntity_shouldLeaveIdEmpty() {
        Category category = mapper.toEntity(new CategoryRequest("Books", "Novels"));

        assertThat(category.getId()).isNull();
        assertThat(category.getName()).isEqualTo("Books");
        assertThat(category.getDescription()).isEqualTo("Novels");
    }

    @Test
    void update_shouldKeepId() {
        Category category = category();

        mapper.update(new CategoryRequest("Books", null), category);

        assertThat(category.getId()).isEqualTo(1L);
        assertThat(category.getName()).isEqualTo("Books");
        assertThat(category.getDescription()).isNull(); // PUT replaces the whole category
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Electronics");
        category.setDescription("Phones and laptops");
        return category;
    }
}
