package com.monssef.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String tokenType;  // always "Bearer": sent back as "Authorization: Bearer <token>"
    private long expiresIn;    // token lifetime in seconds
}
