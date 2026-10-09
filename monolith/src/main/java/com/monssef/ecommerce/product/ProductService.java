package com.monssef.ecommerce.product;

import com.monssef.ecommerce.category.Category;
import com.monssef.ecommerce.category.CategoryService;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository repository;
    private final CategoryService categoryService; // used to validate the category of a product

    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional
    public Product create(Product product) {
        product.setId(null); // ignore any id sent by the client
        product.setCategory(resolveCategory(product.getCategory()));
        return repository.save(product);
    }

    @Transactional
    public Product update(Long id, Product data) {
        Product product = findById(id);
        product.setName(data.getName());
        product.setDescription(data.getDescription());
        product.setPrice(data.getPrice());
        product.setCategory(resolveCategory(data.getCategory()));
        return product; // managed entity: Hibernate saves the changes at commit
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
    }

    // The client only sends "category": { "id": 1 }.
    // We load the real category: 400 if the id is missing, 404 if it does not exist.
    private Category resolveCategory(Category ref) {
        if (ref == null || ref.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category.id is required");
        }
        return categoryService.findById(ref.getId());
    }
}