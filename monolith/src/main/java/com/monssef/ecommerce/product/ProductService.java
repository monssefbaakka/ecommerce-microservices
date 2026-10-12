package com.monssef.ecommerce.product;

import com.monssef.ecommerce.category.Category;
import com.monssef.ecommerce.category.CategoryService;
import com.monssef.ecommerce.common.ResourceNotFoundException;
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

    public Page<Product> findAll(String name, Long categoryId, Pageable pageable) {
        boolean hasName = name != null && !name.isBlank();
        if (hasName && categoryId != null) {
            return productRepository.findByNameContainingIgnoreCaseAndCategoryId(name, categoryId, pageable);
        }
        if (hasName) {
            return productRepository.findByNameContainingIgnoreCase(name, pageable);
        }
        if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId, pageable);
        }
        return productRepository.findAll(pageable);
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional
    public Product create(Product product) {
        product.setId(null); // ignore any id sent by the client
        product.setCategory(resolveCategory(product.getCategory()));
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, Product data) {
        Product existing = findById(id);
        existing.setName(data.getName());
        existing.setDescription(data.getDescription());
        existing.setPrice(data.getPrice());
        existing.setCategory(resolveCategory(data.getCategory()));
        return productRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Product existing = findById(id);
        productRepository.delete(existing);
    }

    // Validate the category of a product: 400 if no id, 404 if not found
    private Category resolveCategory(Category category) {
        if (category == null || category.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category id is required");
        }
        return categoryService.findById(category.getId());
    }
}