package com.monssef.ecommerce.product;

import com.monssef.ecommerce.product.dto.ProductDTO;
import com.monssef.ecommerce.product.dto.ProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    void findAll_shouldReturnPaginatedProducts() throws Exception {
        ProductDTO dto = new ProductDTO(
                1L, "iPhone 15", "128 GB", new BigDecimal("999.99"), 1L, Instant.now());
        Page<ProductDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
        when(productService.findAll(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("iPhone 15"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(productService).findAll(null, null, any());
    }

    @Test
    void findById_shouldReturnProduct_whenExists() throws Exception {
        ProductDTO dto = new ProductDTO(
                1L, "iPhone 15", "128 GB", new BigDecimal("999.99"), 1L, Instant.now());
        when(productService.findById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 15"));

        verify(productService).findById(1L);
    }

    @Test
    void findById_shouldReturn404_whenNotFound() throws Exception {
        when(productService.findById(999L))
                .thenThrow(new com.monssef.ecommerce.common.ResourceNotFoundException("Product", 999L));

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn201_whenValid() throws Exception {
        ProductRequest request = new ProductRequest(
                "iPhone 15", "128 GB", new BigDecimal("999.99"), 1L);
        ProductDTO dto = new ProductDTO(
                1L, "iPhone 15", "128 GB", new BigDecimal("999.99"), 1L, Instant.now());
        when(productService.create(any(ProductRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.price").value(999.99));

        verify(productService).create(any(ProductRequest.class));
    }

    @Test
    void create_shouldReturn400_whenBlankName() throws Exception {
        ProductRequest request = new ProductRequest(
                "", "description", new BigDecimal("10.00"), 1L);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn400_whenPriceIsZero() throws Exception {
        ProductRequest request = new ProductRequest(
                "Free phone", "description", BigDecimal.ZERO, 1L);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_shouldReturnUpdatedProduct_whenValid() throws Exception {
        ProductRequest request = new ProductRequest(
                "iPhone 15", "256 GB", new BigDecimal("1099.99"), 1L);
        ProductDTO dto = new ProductDTO(
                1L, "iPhone 15", "256 GB", new BigDecimal("1099.99"), 1L, Instant.now());
        when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.price").value(1099.99));

        verify(productService).update(eq(1L), any(ProductRequest.class));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        doNothing().when(productService).delete(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }
}
