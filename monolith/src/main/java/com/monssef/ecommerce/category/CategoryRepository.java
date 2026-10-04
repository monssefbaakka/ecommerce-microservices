package com.monssef.ecommerce.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Spring Data generates the query from the method name
    boolean existsByNameIgnoreCase(String name);
}