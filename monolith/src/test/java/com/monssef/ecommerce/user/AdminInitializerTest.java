package com.monssef.ecommerce.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Plain unit test: no Spring context
class AdminInitializerTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void shouldCreateAdmin_whenConfiguredAndEmailFree() {
        when(repository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("admin-pass")).thenReturn("hashed");

        initializer(" Admin@Example.com ", "admin-pass").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getPassword()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldDoNothing_whenNotConfigured() {
        initializer("", "").run(null);

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void shouldNotTouchExistingAccount_whenEmailAlreadyUsed() {
        when(repository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(true);

        initializer("admin@example.com", "admin-pass").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldFail_whenPasswordTooShort() {
        assertThatThrownBy(() -> initializer("admin@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).save(any());
    }

    private AdminInitializer initializer(String email, String password) {
        return new AdminInitializer(repository, passwordEncoder, email, password);
    }
}
