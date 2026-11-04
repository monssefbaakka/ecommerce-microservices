package com.monssef.productservice.stock;

import com.monssef.productservice.auth.JwtService;
import com.monssef.productservice.config.SecurityConfig;
import com.monssef.productservice.product.InternalProductController;
import com.monssef.productservice.product.ProductService;
import com.monssef.productservice.product.dto.ProductDTO;
import com.monssef.productservice.stock.dto.ReservedItem;
import com.monssef.productservice.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The /internal endpoints with real signed JWTs: only the SERVICE role of order-service gets in,
 * a USER or even an ADMIN account cannot change the stock without placing an order.
 */
@WebMvcTest({StockController.class, InternalProductController.class})
@Import({SecurityConfig.class, JwtService.class}) // real JwtService, not a mock
@TestPropertySource(properties = "jwt.secret=" + TestTokens.SECRET)
class InternalEndpointsSecurityTest {

    private static final String RESERVATION = "{\"items\": [{\"productId\": 1, \"quantity\": 2}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockService stockService;

    @MockBean
    private ProductService productService;

    @Test
    void reserve_shouldReturnReservedItems_whenServiceToken() throws Exception {
        when(stockService.reserve(any())).thenReturn(List.of(
                new ReservedItem(1L, "iPhone 15", new BigDecimal("999.99"), 2)));

        reserve(RESERVATION, token("SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].name").value("iPhone 15"))
                .andExpect(jsonPath("$[0].price").value(999.99))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    void reserve_shouldReturn403_whenUserOrAdminToken() throws Exception {
        reserve(RESERVATION, token("USER")).andExpect(status().isForbidden());
        reserve(RESERVATION, token("ADMIN")).andExpect(status().isForbidden());

        verifyNoInteractions(stockService);
    }

    @Test
    void reserve_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/internal/stock/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVATION))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(stockService);
    }

    @Test
    void reserve_shouldReturn400_whenNoItemsOrQuantityZero() throws Exception {
        reserve("{\"items\": []}", token("SERVICE")).andExpect(status().isBadRequest());
        reserve("{\"items\": [{\"productId\": 1, \"quantity\": 0}]}", token("SERVICE"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stockService);
    }

    @Test
    void release_shouldReturn204_whenServiceToken() throws Exception {
        mockMvc.perform(post("/internal/stock/releases")
                        .header(HttpHeaders.AUTHORIZATION, token("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVATION))
                .andExpect(status().isNoContent());

        verify(stockService).release(any());
    }

    @Test
    void findProducts_shouldReturnTheList_whenServiceToken() throws Exception {
        when(productService.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                new ProductDTO(1L, "iPhone 15", "128 GB", new BigDecimal("999.99"), 1L, 5, Instant.now())));

        mockMvc.perform(get("/internal/products").param("ids", "1,2")
                        .header(HttpHeaders.AUTHORIZATION, token("SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stock").value(5));
    }

    @Test
    void findProducts_shouldReturn403_whenUserToken() throws Exception {
        mockMvc.perform(get("/internal/products").param("ids", "1")
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isForbidden());
    }

    private ResultActions reserve(String body, String authorization) throws Exception {
        return mockMvc.perform(post("/internal/stock/reservations")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(String role) {
        return "Bearer " + TestTokens.create("order-service", role);
    }
}
