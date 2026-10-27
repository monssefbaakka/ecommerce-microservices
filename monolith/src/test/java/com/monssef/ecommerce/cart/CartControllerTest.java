package com.monssef.ecommerce.cart;

import com.monssef.ecommerce.auth.JwtService;
import com.monssef.ecommerce.cart.dto.AddCartItemRequest;
import com.monssef.ecommerce.cart.dto.CartDTO;
import com.monssef.ecommerce.cart.dto.CartItemDTO;
import com.monssef.ecommerce.cart.dto.UpdateCartItemRequest;
import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class) // @WebMvcTest does not load @Configuration classes
class CartControllerTest {

    private static final String EMAIL = "john@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @MockBean
    private JwtService jwtService; // required by SecurityConfig (JWT filter)

    @Test
    @WithMockUser(username = EMAIL) // the email is what the JWT filter puts in the principal
    void getCart_shouldReturnCartOfLoggedInUser() throws Exception {
        when(cartService.getCart(EMAIL)).thenReturn(cartWithOneItem());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.items[0].subtotal").value(1999.98))
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.total").value(1999.98));

        verify(cartService).getCart(EMAIL);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void addItem_shouldReturnUpdatedCart_whenValid() throws Exception {
        when(cartService.addItem(eq(EMAIL), any(AddCartItemRequest.class))).thenReturn(cartWithOneItem());

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(1L, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        verify(cartService).addItem(eq(EMAIL), any(AddCartItemRequest.class));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void addItem_shouldReturn400_whenQuantityIsZero() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(1L, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void addItem_shouldReturn400_whenProductIdMissing() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(null, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("productId"));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void addItem_shouldReturn404_whenProductDoesNotExist() throws Exception {
        when(cartService.addItem(eq(EMAIL), any(AddCartItemRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product", 99L));

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(99L, 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateItem_shouldReturnUpdatedCart() throws Exception {
        when(cartService.updateItem(eq(EMAIL), eq(1L), any(UpdateCartItemRequest.class)))
                .thenReturn(cartWithOneItem());

        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateItem_shouldReturn400_whenQuantityTooLarge() throws Exception {
        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCartItemRequest(CartService.MAX_QUANTITY + 1))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void removeItem_shouldReturn404_whenProductNotInCart() throws Exception {
        when(cartService.removeItem(EMAIL, 5L))
                .thenThrow(new ResourceNotFoundException("Cart item for product", 5L));

        mockMvc.perform(delete("/api/cart/items/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cart item for product with id 5 not found"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void clear_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/cart"))
                .andExpect(status().isNoContent());

        verify(cartService).clear(EMAIL);
    }

    @Test
    void getCart_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getCart_shouldBeAllowed_whenAdmin() throws Exception {
        // The cart is open to any logged-in user, not only USER
        when(cartService.getCart("admin@example.com")).thenReturn(new CartDTO(List.of(), 0, BigDecimal.ZERO));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk());
    }

    private CartDTO cartWithOneItem() {
        CartItemDTO item = new CartItemDTO(
                1L, "iPhone 15", new BigDecimal("999.99"), 2, new BigDecimal("1999.98"));
        return new CartDTO(List.of(item), 2, new BigDecimal("1999.98"));
    }
}
