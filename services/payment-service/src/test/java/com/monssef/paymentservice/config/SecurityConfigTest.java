package com.monssef.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monssef.paymentservice.auth.JwtService;
import com.monssef.paymentservice.payment.PaymentController;
import com.monssef.paymentservice.payment.PaymentService;
import com.monssef.paymentservice.payment.PaymentStatus;
import com.monssef.paymentservice.payment.dto.PaymentDTO;
import com.monssef.paymentservice.payment.dto.PaymentRequest;
import com.monssef.paymentservice.support.TestTokens;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end checks of the security rules with real signed JWTs.
 * The controller tests use @WithMockUser, which skips the JWT filter: here the token
 * goes through the whole chain (header -> JwtAuthenticationFilter -> SecurityConfig rules).
 */
@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtService.class}) // real JwtService, not a mock
@TestPropertySource(properties = "jwt.secret=" + TestTokens.SECRET)
class SecurityConfigTest {

    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci0x";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    // --- Any valid token, whatever the role ---

    @Test
    void pay_shouldUseTheEmailOfTheToken_whenUserToken() throws Exception {
        when(paymentService.pay(eq("john@example.com"), any(PaymentRequest.class))).thenReturn(new PaymentDTO(
                3L, 7L, new BigDecimal("1999.98"), PaymentStatus.SUCCEEDED, "4242", Instant.now()));

        pay("Bearer " + tokenFor("john@example.com", "USER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));

        verify(paymentService).pay(eq("john@example.com"), any(PaymentRequest.class));
    }

    @Test
    void getPayments_shouldReturn200_whenAdminToken() throws Exception {
        when(paymentService.findAll(eq("admin@example.com"), any())).thenReturn(Page.empty());

        getPayments("Bearer " + tokenFor("admin@example.com", "ADMIN"))
                .andExpect(status().isOk());
    }

    // --- No token or a rejected one: always 401, never reaches the controller ---

    @Test
    void getPayments_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/payments"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void pay_shouldReturn401_whenTokenIsMalformed() throws Exception {
        assertUnauthorized(pay("Bearer not-a-jwt"));
    }

    @Test
    void pay_shouldReturn401_whenTokenIsExpired() throws Exception {
        String token = TestTokens.create(TestTokens.SECRET, "john@example.com", "USER", Duration.ofSeconds(-1));

        assertUnauthorized(pay("Bearer " + token));
    }

    @Test
    void pay_shouldReturn401_whenTokenIsSignedWithAnotherKey() throws Exception {
        // Forged token to pay as someone else: right format, wrong signature
        String token = TestTokens.create(OTHER_SECRET, "john@example.com", "USER", Duration.ofHours(1));

        assertUnauthorized(pay("Bearer " + token));
    }

    @Test
    void pay_shouldReturn401_whenRoleClaimIsMissing() throws Exception {
        // Valid signature, but the filter needs both the subject and the role
        String token = Jwts.builder()
                .subject("john@example.com")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestTokens.SECRET)))
                .compact();

        assertUnauthorized(pay("Bearer " + token));
    }

    @Test
    void pay_shouldReturn401_whenBearerPrefixIsMissing() throws Exception {
        assertUnauthorized(pay(tokenFor("john@example.com", "USER")));
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
        when(paymentService.findAll(eq("john@example.com"), any())).thenReturn(Page.empty());

        getPayments("Bearer " + tokenFor("john@example.com", "USER"))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private ResultActions pay(String authorization) throws Exception {
        return mockMvc.perform(post("/api/payments")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentRequest(7L, "4242424242424242"))));
    }

    private ResultActions getPayments(String authorization) throws Exception {
        return mockMvc.perform(get("/api/payments").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private void assertUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
        verifyNoInteractions(paymentService);
    }

    private String tokenFor(String email, String role) {
        return TestTokens.create(email, role);
    }
}
