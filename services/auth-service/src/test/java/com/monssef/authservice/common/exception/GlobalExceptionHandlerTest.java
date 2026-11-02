package com.monssef.authservice.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Standalone MockMvc: only a fake controller and the handler, no Spring context, no security
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationError_shouldReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/fake/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/fake/items"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Le nom est obligatoire"));
    }

    @Test
    void malformedJson_shouldReturn400() throws Exception {
        mockMvc.perform(post("/fake/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Failed to read request"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void unsupportedMethod_shouldReturn405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/fake/items"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void unsupportedContentType_shouldReturn415() throws Exception {
        mockMvc.perform(post("/fake/items").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void unknownUrl_shouldReturn404() throws Exception {
        mockMvc.perform(get("/fake/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/fake/unknown"));
    }

    @Test
    void responseStatusException_shouldKeepStatusAndReason() throws Exception {
        mockMvc.perform(get("/fake/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already registered: john@example.com"));
    }

    @Test
    void dataIntegrityViolation_shouldReturn409WithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/fake/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Violation d'intégrité de données"))
                .andExpect(jsonPath("$.message").value(not(containsString("users_email_key"))));
    }

    @Test
    void unexpectedException_shouldReturn500WithoutItsMessage() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("Une erreur inattendue est survenue"))
                .andExpect(jsonPath("$.path").value("/fake/boom"));
    }

    record FakeRequest(@NotBlank(message = "Le nom est obligatoire") String name) {}

    @RestController
    @RequestMapping("/fake")
    static class FakeController {

        @PostMapping("/items")
        String create(@Valid @RequestBody FakeRequest request) {
            return request.name();
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered: john@example.com");
        }

        // Two registrations of the same email at the same time: the unique constraint catches the second
        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("duplicate key value violates unique constraint \"users_email_key\""));
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("connection string with password=secret");
        }
    }
}
