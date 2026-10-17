package com.monssef.ecommerce.category;

import com.monssef.ecommerce.category.dto.CategoryDTO;
import com.monssef.ecommerce.category.dto.CategoryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService service;

    @Test
    void findAll_shouldReturnAllCategories() throws Exception {
        CategoryDTO dto = new CategoryDTO(1L, "Electronics", "Phones and laptops");
        when(service.findAll()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[0].description").value("Phones and laptops"));

        verify(service).findAll();
    }

    @Test
    void findById_shouldReturnCategory_whenExists() throws Exception {
        CategoryDTO dto = new CategoryDTO(1L, "Electronics", "Phones and laptops");
        when(service.findById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Electronics"));

        verify(service).findById(1L);
    }

    @Test
    void findById_shouldReturn404_whenNotFound() throws Exception {
        when(service.findById(999L))
                .thenThrow(new com.monssef.ecommerce.common.ResourceNotFoundException("Category", 999L));

        mockMvc.perform(get("/api/categories/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn201_whenValid() throws Exception {
        CategoryRequest request = new CategoryRequest("Electronics", "Phones and laptops");
        CategoryDTO dto = new CategoryDTO(1L, "Electronics", "Phones and laptops");
        when(service.create(any(CategoryRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Electronics"));

        verify(service).create(any(CategoryRequest.class));
    }

    @Test
    void create_shouldReturn400_whenBlankName() throws Exception {
        CategoryRequest request = new CategoryRequest("", "description");

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_shouldReturnUpdatedCategory_whenValid() throws Exception {
        CategoryRequest request = new CategoryRequest("Electronics", "Updated description");
        CategoryDTO dto = new CategoryDTO(1L, "Electronics", "Updated description");
        when(service.update(eq(1L), any(CategoryRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.description").value("Updated description"));

        verify(service).update(eq(1L), any(CategoryRequest.class));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isNoContent());

        verify(service).delete(1L);
    }
}
