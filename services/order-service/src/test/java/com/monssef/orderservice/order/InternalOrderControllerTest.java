package com.monssef.orderservice.order;

import com.monssef.orderservice.auth.JwtService;
import com.monssef.orderservice.common.ResourceNotFoundException;
import com.monssef.orderservice.config.SecurityConfig;
import com.monssef.orderservice.order.dto.OrderSummaryDTO;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalOrderController.class)
@Import(SecurityConfig.class) // @WebMvcTest does not load @Configuration classes
@WithMockUser(username = "payment-service", roles = "SERVICE")
class InternalOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService; // required by SecurityConfig (JWT filter)

    @Test
    void findById_shouldReturnSummary() throws Exception {
        when(orderService.findSummary(7L)).thenReturn(summary(OrderStatus.PENDING));

        mockMvc.perform(get("/internal/orders/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.userEmail").value("john@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(1999.98));
    }

    @Test
    void findById_shouldReturn404_whenOrderMissing() throws Exception {
        when(orderService.findSummary(99L)).thenThrow(new ResourceNotFoundException("Order", 99L));

        mockMvc.perform(get("/internal/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id 99 not found"));
    }

    @Test
    void markPaid_shouldReturnPaidOrder() throws Exception {
        when(orderService.markPaid(7L)).thenReturn(summary(OrderStatus.PAID));

        mockMvc.perform(post("/internal/orders/7/paid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(orderService).markPaid(7L);
    }

    @Test
    void markPaid_shouldReturn409_whenOrderNotPending() throws Exception {
        when(orderService.markPaid(7L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Order 7 is PAID, only a PENDING order can be paid"));

        mockMvc.perform(post("/internal/orders/7/paid"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order 7 is PAID, only a PENDING order can be paid"));
    }

    private OrderSummaryDTO summary(OrderStatus status) {
        return new OrderSummaryDTO(7L, "john@example.com", status, new BigDecimal("1999.98"));
    }
}
