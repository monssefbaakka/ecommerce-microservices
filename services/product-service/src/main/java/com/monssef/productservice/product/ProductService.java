package com.monssef.productservice.product;

import com.monssef.productservice.category.Category;
import com.monssef.productservice.category.CategoryService;
import com.monssef.productservice.common.ResourceNotFoundException;
import com.monssef.productservice.product.dto.ProductDTO;
import com.monssef.productservice.product.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductMapper mapper;

    public Page<ProductDTO> findAll(String name, Long categoryId, Pageable pageable) {
        boolean hasName = name != null && !name.isBlank();
        Page<Product> page;
        if (hasName && categoryId != null) {
            page = productRepository.findByNameContainingIgnoreCaseAndCategoryId(name, categoryId, pageable);
        } else if (hasName) {
            page = productRepository.findByNameContainingIgnoreCase(name, pageable);
        } else if (categoryId != null) {
            page = productRepository.findByCategoryId(categoryId, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }
        return page.map(mapper::toDto);
    }

    public ProductDTO findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return mapper.toDto(product);
    }

    @Transactional
    public ProductDTO create(ProductRequest request) {
        Product product = mapper.toEntity(request);
        product.setCategory(resolveCategory(request.getCategoryId()));
        Product saved = productRepository.save(product);
        return mapper.toDto(saved);
    }

    @Transactional
    public ProductDTO update(Long id, ProductRequest request) {
        Product existing = findEntityById(id);
        mapper.update(request, existing);
        existing.setCategory(resolveCategory(request.getCategoryId()));
        return mapper.toDto(productRepository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        Product existing = findEntityById(id);
        productRepository.delete(existing);
    }

    private Product findEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    // Validate the category of a product: 400 if no id, 404 if not found
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category id is required");
        }
        return categoryService.findEntityById(categoryId);
    }
}
