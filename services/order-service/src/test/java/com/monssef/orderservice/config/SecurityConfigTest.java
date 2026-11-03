package com.monssef.orderservice.config;

import com.monssef.orderservice.auth.JwtService;
import com.monssef.orderservice.cart.CartController;
import com.monssef.orderservice.cart.CartService;
import com.monssef.orderservice.cart.dto.CartDTO;
import com.monssef.orderservice.order.OrderController;
import com.monssef.orderservice.order.OrderService;
import com.monssef.orderservice.order.OrderStatus;
import com.monssef.orderservice.order.dto.OrderDTO;
import com.monssef.orderservice.support.TestTokens;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end checks of the security rules with real signed JWTs.
 * The controller tests use @WithMockUser, which skips the JWT filter: here the token
 * goes through the whole chain (header -> JwtAuthenticationFilter -> SecurityConfig rules).
 */
@WebMvcTest({CartController.class, OrderController.class})
@Import({SecurityConfig.class, JwtService.class}) // real JwtService, not a mock
@TestPropertySource(properties = "jwt.secret=" + TestTokens.SECRET)
class SecurityConfigTest {

    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci0x";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @MockBean
    private OrderService orderService;

    // --- Any valid token, whatever the role ---

    @Test
    void getCart_shouldUseTheEmailOfTheToken_whenUserToken() throws Exception {
        when(cartService.getCart("john@example.com")).thenReturn(emptyCart());

        getCart("Bearer " + tokenFor("john@example.com", "USER"))
                .andExpect(status().isOk());

        verify(cartService).getCart("john@example.com");
    }

    @Test
    void getCart_shouldReturn200_whenAdminToken() throws Exception {
        when(cartService.getCart("admin@example.com")).thenReturn(emptyCart());

        getCart("Bearer " + tokenFor("admin@example.com", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void placeOrder_shouldReturn201_whenUserToken() throws Exception {
        when(orderService.placeOrder("john@example.com")).thenReturn(new OrderDTO(
                7L, OrderStatus.PENDING, List.of(), 0, BigDecimal.ZERO, Instant.now()));

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("john@example.com", "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    // --- No token or a rejected one: always 401, never reaches the controller ---

    @Test
    void getCart_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/cart"));

        verifyNoInteractions(cartService);
    }

    @Test
    void placeOrder_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(orderService);
    }

    @Test
    void getCart_shouldReturn401_whenTokenIsMalformed() throws Exception {
        assertUnauthorized(getCart("Bearer not-a-jwt"));
    }

    @Test
    void getCart_shouldReturn401_whenTokenIsExpired() throws Exception {
        String token = TestTokens.create(TestTokens.SECRET, "john@example.com", "USER", Duration.ofSeconds(-1));

        assertUnauthorized(getCart("Bearer " + token));
    }

    @Test
    void getCart_shouldReturn401_whenTokenIsSignedWithAnotherKey() throws Exception {
        // Forged token to read the cart of someone else: right format, wrong signature
        String token = TestTokens.create(OTHER_SECRET, "john@example.com", "USER", Duration.ofHours(1));

        assertUnauthorized(getCart("Bearer " + token));
    }

    @Test
    void getCart_shouldReturn401_whenRoleClaimIsMissing() throws Exception {
        // Valid signature, but the filter needs both the subject and the role
        String token = Jwts.builder()
                .subject("john@example.com")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestTokens.SECRET)))
                .compact();

        assertUnauthorized(getCart("Bearer " + token));
    }

    @Test
    void getCart_shouldReturn401_whenBearerPrefixIsMissing() throws Exception {
        assertUnauthorized(getCart(tokenFor("john@example.com", "USER")));
    }

    @Test
    void unknownEndpoint_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/unknown"));
    }

    // --- Stateless ---

    @Test
    void shouldNotCreateSession_whenAuthenticatedWithToken() throws Exception {
        when(cartService.getCart("john@example.com")).thenReturn(emptyCart());

        getCart("Bearer " + tokenFor("john@example.com", "USER"))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private ResultActions getCart(String authorization) throws Exception {
        return mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private void assertUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
        verifyNoInteractions(cartService);
    }

    private CartDTO emptyCart() {
        return new CartDTO(List.of(), 0, BigDecimal.ZERO);
    }

    private String tokenFor(String email, String role) {
        return TestTokens.create(email, role);
    }
}
