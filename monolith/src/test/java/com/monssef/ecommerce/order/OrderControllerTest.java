package com.monssef.ecommerce.order;

import com.monssef.ecommerce.auth.JwtService;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.config.SecurityConfig;
import com.monssef.ecommerce.order.dto.OrderDTO;
import com.monssef.ecommerce.order.dto.OrderItemDTO;
import com.monssef.ecommerce.product.InsufficientStockException;
import com.monssef.ecommerce.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class) // @WebMvcTest does not load @Configuration classes
class OrderControllerTest {

    private static final String EMAIL = "john@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService; // required by SecurityConfig (JWT filter)

    @Test
    @WithMockUser(username = EMAIL) // the email is what the JWT filter puts in the principal
    void placeOrder_shouldReturn201WithLocation() throws Exception {
        when(orderService.placeOrder(EMAIL)).thenReturn(order());

        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/orders/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(999.99))
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.total").value(1999.98));

        verify(orderService).placeOrder(EMAIL);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void placeOrder_shouldReturn400_whenCartIsEmpty() throws Exception {
        when(orderService.placeOrder(EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty"));

        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void placeOrder_shouldReturn409_whenNotEnoughStock() throws Exception {
        when(orderService.placeOrder(EMAIL)).thenThrow(new InsufficientStockException(product()));

        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock for product 1 (iPhone 15): 1 left"));
    }

    @Test
    void placeOrder_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void placeOrder_shouldBeAllowed_whenAdmin() throws Exception {
        // Ordering is open to any logged-in user, not only USER
        when(orderService.placeOrder("admin@example.com")).thenReturn(order());

        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void findById_shouldReturnOrder() throws Exception {
        when(orderService.findById(EMAIL, 7L)).thenReturn(order());

        mockMvc.perform(get("/api/orders/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.items[0].subtotal").value(1999.98));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void findById_shouldReturn404_whenNotFound() throws Exception {
        when(orderService.findById(EMAIL, 99L)).thenThrow(new ResourceNotFoundException("Order", 99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id 99 not found"));
    }

    @Test
    void findById_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/orders/7"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    private Product product() {
        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone 15");
        product.setStock(1);
        return product;
    }

    private OrderDTO order() {
        OrderItemDTO item = new OrderItemDTO(
                1L, "iPhone 15", new BigDecimal("999.99"), 2, new BigDecimal("1999.98"));
        return new OrderDTO(7L, OrderStatus.PENDING, List.of(item), 2,
                new BigDecimal("1999.98"), Instant.parse("2026-10-27T10:00:00Z"));
    }
}
