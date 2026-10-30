package com.monssef.ecommerce.product;

import com.monssef.ecommerce.product.dto.ProductDTO;
import com.monssef.ecommerce.product.dto.ProductRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// The category is left to ProductService: it loads it from categoryId (400 or 404 if invalid)
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    ProductDTO toDto(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Product toEntity(ProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(ProductRequest request, @MappingTarget Product product);
}
