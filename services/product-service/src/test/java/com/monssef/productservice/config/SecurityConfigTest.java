package com.monssef.productservice.config;

import com.monssef.productservice.auth.JwtService;
import com.monssef.productservice.category.CategoryController;
import com.monssef.productservice.category.CategoryService;
import com.monssef.productservice.product.ProductController;
import com.monssef.productservice.product.ProductService;
import com.monssef.productservice.product.dto.ProductDTO;
import com.monssef.productservice.product.dto.ProductRequest;
import com.monssef.productservice.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end checks of the security rules with real signed JWTs.
 * The controller tests use @WithMockUser, which skips the JWT filter: here the token
 * goes through the whole chain (header -> JwtAuthenticationFilter -> SecurityConfig rules).
 */
@WebMvcTest({ProductController.class, CategoryController.class})
@Import({SecurityConfig.class, JwtService.class}) // real JwtService, not a mock
@TestPropertySource(properties = "jwt.secret=" + TestTokens.SECRET)
class SecurityConfigTest {

    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci0x";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private CategoryService categoryService;

    // --- Public reads ---

    @Test
    void getProducts_shouldReturn200_whenAnonymous() throws Exception {
        when(productService.findAll(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void getProducts_shouldReturn200_whenTokenIsInvalid() throws Exception {
        // A bad token makes the request anonymous, it does not block public endpoints
        when(productService.findAll(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    // --- Writes with a valid token ---

    @Test
    void createProduct_shouldReturn201_whenAdminToken() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(new ProductDTO(
                1L, "iPhone 15", "128 GB", new BigDecimal("999.99"), 1L, 10, Instant.now()));

        createProduct("Bearer " + tokenFor("admin@example.com", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createProduct_shouldReturn403_whenUserToken() throws Exception {
        createProduct("Bearer " + tokenFor("john@example.com", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(productService);
    }

    @Test
    void deleteCategory_shouldReturn204_whenAdminToken() throws Exception {
        mockMvc.perform(delete("/api/categories/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin@example.com", "ADMIN")))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(1L);
    }

    @Test
    void deleteCategory_shouldReturn403_whenUserToken() throws Exception {
        mockMvc.perform(delete("/api/categories/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("john@example.com", "USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    // --- Writes with a rejected token: always 401, never reaches the controller ---

    @Test
    void createProduct_shouldReturn401_whenTokenIsMalformed() throws Exception {
        assertUnauthorized(createProduct("Bearer not-a-jwt"));
    }

    @Test
    void createProduct_shouldReturn401_whenTokenIsExpired() throws Exception {
        String token = TestTokens.create(TestTokens.SECRET, "admin@example.com", "ADMIN", Duration.ofSeconds(-1));

        assertUnauthorized(createProduct("Bearer " + token));
    }

    @Test
    void createProduct_shouldReturn401_whenTokenIsSignedWithAnotherKey() throws Exception {
        // Forged ADMIN token: right format, wrong signature
        String token = TestTokens.create(OTHER_SECRET, "admin@example.com", "ADMIN", Duration.ofHours(1));

        assertUnauthorized(createProduct("Bearer " + token));
    }

    @Test
    void createProduct_shouldReturn401_whenRoleClaimIsMissing() throws Exception {
        // Valid signature, but the filter needs both the subject and the role
        String token = Jwts.builder()
                .subject("admin@example.com")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestTokens.SECRET)))
                .compact();

        assertUnauthorized(createProduct("Bearer " + token));
    }

    @Test
    void createProduct_shouldReturn401_whenBearerPrefixIsMissing() throws Exception {
        String token = tokenFor("admin@example.com", "ADMIN");

        assertUnauthorized(createProduct(token));
    }

    // --- Any other endpoint needs a logged-in user ---

    @Test
    void unknownEndpoint_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/unknown"));
    }

    // --- Stateless ---

    @Test
    void shouldNotCreateSession_whenAuthenticatedWithToken() throws Exception {
        mockMvc.perform(delete("/api/categories/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin@example.com", "ADMIN")))
                .andExpect(status().isNoContent())
                .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private ResultActions createProduct(String authorization) throws Exception {
        ProductRequest request = new ProductRequest(
                "iPhone 15", "128 GB", new BigDecimal("999.99"), 1L, 10);
        return mockMvc.perform(post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void assertUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
        verifyNoInteractions(productService);
    }

    private String tokenFor(String email, String role) {
        return TestTokens.create(email, role);
    }
}
