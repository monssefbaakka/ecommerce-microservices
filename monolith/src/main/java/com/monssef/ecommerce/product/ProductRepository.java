package com.monssef.ecommerce.product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
Page<Product> findByNameContainingIgnoreCaseAndCategoryId(String name, Long categoryId, Pageable pageable);
    // Empty for now: JpaRepository already gives us findAll, findById, save, delete...
    // Search and pagination methods are added in issue #9.
}
