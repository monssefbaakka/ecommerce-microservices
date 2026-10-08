package com.monssef.ecommerce.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // Empty for now: JpaRepository already gives us findAll, findById, save, delete...
    // Search and pagination methods are added in issue #9.
}
