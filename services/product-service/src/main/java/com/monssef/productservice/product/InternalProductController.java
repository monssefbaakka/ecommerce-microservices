package com.monssef.productservice.product;

import com.monssef.productservice.product.dto.ProductDTO;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// For order-service only (role SERVICE): not routed by the gateway, hidden from Swagger UI
@Hidden
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    // GET /internal/products?ids=1,2,3: name, current price and stock of the products of a cart
    @GetMapping
    public List<ProductDTO> findAllById(@RequestParam List<Long> ids) {
        return productService.findAllById(ids);
    }
}
