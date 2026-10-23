package com.monssef.ecommerce.auth;

import com.monssef.ecommerce.auth.dto.AuthResponse;
import com.monssef.ecommerce.auth.dto.LoginRequest;
import com.monssef.ecommerce.user.Role;
import com.monssef.ecommerce.user.User;
import com.monssef.ecommerce.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // plain unit test: no Spring context, no database
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService service;

    @Test
    void login_shouldReturnToken_whenCredentialsAreValid() {
        User user = user("john@example.com", "hashed");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpiration()).thenReturn(Duration.ofHours(1));

        AuthResponse result = service.login(new LoginRequest("john@example.com", "secret123"));

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getExpiresIn()).isEqualTo(3600);
    }

    @Test
    void login_shouldNormalizeEmail() {
        User user = user("john@example.com", "hashed");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpiration()).thenReturn(Duration.ofHours(1));

        service.login(new LoginRequest("  John@Example.COM ", "secret123"));

        verify(userRepository).findByEmail("john@example.com");
    }

    @Test
    void login_shouldThrow401_whenEmailUnknown() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertUnauthorized(new LoginRequest("ghost@example.com", "secret123"));
    }

    @Test
    void login_shouldThrow401_whenPasswordWrong() {
        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user("john@example.com", "hashed")));
        when(passwordEncoder.matches("wrong-pass", "hashed")).thenReturn(false);

        assertUnauthorized(new LoginRequest("john@example.com", "wrong-pass"));
    }

    private void assertUnauthorized(LoginRequest request) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(jwtService, never()).generateToken(any());
    }

    private User user(String email, String hashedPassword) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setRole(Role.USER);
        return user;
    }
}
