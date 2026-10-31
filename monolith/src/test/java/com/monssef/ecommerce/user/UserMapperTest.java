package com.monssef.ecommerce.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monssef.ecommerce.user.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test of the generated mapper: no Spring context, no database
class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toDto_shouldCopyAccountFields() {
        UserDTO dto = mapper.toDto(user());

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getEmail()).isEqualTo("john@example.com");
        assertThat(dto.getRole()).isEqualTo(Role.ADMIN);
        assertThat(dto.getCreatedAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
    }

    @Test
    void toDto_shouldNeverExposePassword() throws Exception {
        UserDTO dto = mapper.toDto(user());

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(dto);

        assertThat(json).doesNotContain("password").doesNotContain("hashed");
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("john@example.com");
        user.setPassword("hashed");
        user.setRole(Role.ADMIN);
        user.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return user;
    }
}
