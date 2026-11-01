package com.monssef.authservice.user.dto;

import com.monssef.authservice.user.Role;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Never expose the password, even hashed
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;

    private String email;

    private Role role;

    private Instant createdAt;
}
