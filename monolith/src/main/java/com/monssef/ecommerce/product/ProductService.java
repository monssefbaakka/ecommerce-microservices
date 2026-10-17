package com.monssef.ecommerce.product;

import com.monssef.ecommerce.category.Category;
import com.monssef.ecommerce.category.CategoryService;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.product.dto.ProductDTO;
import com.monssef.ecommerce.product.dto.ProductRequest;
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
        return page.map(this::toDto);
    }

    public ProductDTO findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return toDto(product);
    }

    @Transactional
    public ProductDTO create(ProductRequest request) {
        Product product = new Product();
        product.setId(null); // ignore any id sent by the client
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(resolveCategory(request.getCategoryId()));
        Product saved = productRepository.save(product);
        return toDto(saved);
    }

    @Transactional
    public ProductDTO update(Long id, ProductRequest request) {
        Product existing = repositoryFindById(id);
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setCategory(resolveCategory(request.getCategoryId()));
        return toDto(productRepository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        Product existing = repositoryFindById(id);
        productRepository.delete(existing);
    }

    private Product repositoryFindById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    // Validate the category of a product: 400 if no id, 404 if not found
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category id is required");
        }
        return categoryService.findById(categoryId);
    }

    private ProductDTO toDto(Product product) {
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCreatedAt()
        );
    }
}