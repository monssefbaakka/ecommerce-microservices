package com.monssef.paymentservice.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.paymentservice.auth.JwtService;
import com.monssef.paymentservice.common.ResourceNotFoundException;
import com.monssef.paymentservice.config.SecurityConfig;
import com.monssef.paymentservice.payment.dto.PaymentDTO;
import com.monssef.paymentservice.payment.dto.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class) // @WebMvcTest does not load @Configuration classes
class PaymentControllerTest {

    private static final String EMAIL = "john@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtService jwtService; // required by SecurityConfig (JWT filter)

    @Test
    @WithMockUser(username = EMAIL) // the email is what the JWT filter puts in the principal
    void pay_shouldReturn201WithLocation() throws Exception {
        when(paymentService.pay(eq(EMAIL), any(PaymentRequest.class))).thenReturn(payment(PaymentStatus.SUCCEEDED));

        pay(new PaymentRequest(7L, "4242424242424242"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/payments/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.orderId").value(7))
                .andExpect(jsonPath("$.amount").value(1999.98))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.cardLast4").value("4242"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void pay_shouldReturn201_whenCardDeclined() throws Exception {
        when(paymentService.pay(eq(EMAIL), any(PaymentRequest.class))).thenReturn(payment(PaymentStatus.DECLINED));

        pay(new PaymentRequest(7L, "4000000000000002"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void pay_shouldReturn400_whenCardNumberIsNot16Digits() throws Exception {
        pay(new PaymentRequest(7L, "4242-4242"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cardNumber"))
                .andExpect(jsonPath("$.errors[0].message").value("Le numéro de carte doit contenir 16 chiffres"));

        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void pay_shouldReturn400_whenOrderIdMissing() throws Exception {
        pay(new PaymentRequest(null, "4242424242424242"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orderId"));

        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void pay_shouldReturn404_whenOrderNotFound() throws Exception {
        when(paymentService.pay(eq(EMAIL), any(PaymentRequest.class)))
                .thenThrow(new ResourceNotFoundException("Order", 99L));

        pay(new PaymentRequest(99L, "4242424242424242"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id 99 not found"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void pay_shouldReturn409_whenOrderAlreadyPaid() throws Exception {
        when(paymentService.pay(eq(EMAIL), any(PaymentRequest.class))).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "Order 7 is PAID, only a PENDING order can be paid"));

        pay(new PaymentRequest(7L, "4242424242424242"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order 7 is PAID, only a PENDING order can be paid"));
    }

    @Test
    void pay_shouldReturn401_whenAnonymous() throws Exception {
        pay(new PaymentRequest(7L, "4242424242424242"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void findAll_shouldReturnPageOfMyPayments_newestFirst() throws Exception {
        when(paymentService.findAll(eq(EMAIL), any()))
                .thenReturn(new PageImpl<>(List.of(payment(PaymentStatus.SUCCEEDED)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentService).findAll(eq(EMAIL), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void findById_shouldReturnPayment() throws Exception {
        when(paymentService.findById(EMAIL, 3L)).thenReturn(payment(PaymentStatus.SUCCEEDED));

        mockMvc.perform(get("/api/payments/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(7));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void findById_shouldReturn404_whenNotFound() throws Exception {
        when(paymentService.findById(EMAIL, 99L)).thenThrow(new ResourceNotFoundException("Payment", 99L));

        mockMvc.perform(get("/api/payments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payment with id 99 not found"));
    }

    private ResultActions pay(PaymentRequest request) throws Exception {
        return mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private PaymentDTO payment(PaymentStatus status) {
        return new PaymentDTO(3L, 7L, new BigDecimal("1999.98"), status, "4242",
                Instant.parse("2026-11-03T10:00:00Z"));
    }
}
