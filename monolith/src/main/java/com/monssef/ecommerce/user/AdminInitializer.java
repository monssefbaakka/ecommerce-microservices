package com.monssef.ecommerce.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the first ADMIN account at startup, since /api/auth/register only creates USER accounts.
 * Opt-in: does nothing unless both ADMIN_EMAIL and ADMIN_PASSWORD are set.
 */
@Slf4j
@Component
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminInitializer(UserRepository repository, PasswordEncoder passwordEncoder,
                            @Value("${app.admin.email:}") String email,
                            @Value("${app.admin.password:}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        // Same rule as RegisterRequest: fail at startup rather than create a weak admin
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be between 8 and 72 characters");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(normalizedEmail)) {
            // Never promote an existing account: someone else may have registered this email
            log.info("Admin account not created: {} already exists", normalizedEmail);
            return;
        }
        User admin = new User();
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        repository.save(admin);
        log.info("Admin account created: {}", normalizedEmail);
    }
}
