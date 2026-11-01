package com.monssef.authservice.auth;

import com.monssef.authservice.user.UserService;
import com.monssef.authservice.user.dto.RegisterRequest;
import com.monssef.authservice.user.dto.UserDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Same URLs as the monolith: the gateway sends /api/auth/** here without rewriting the path
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // @Valid triggers the validation annotations of RegisterRequest (@Email, @Size...)
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED) // 201, no Location header: there is no GET /api/users/{id}
    public UserDTO register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }
}
