package com.monssef.ecommerce.common.exception;

import com.monssef.ecommerce.common.ResourceNotFoundException;
import com.monssef.ecommerce.product.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

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
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Failed to read request"))
                .andExpect(jsonPath("$.path").value("/fake/items"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void wrongTypeInUrl_shouldReturn400() throws Exception {
        mockMvc.perform(get("/fake/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("abc")));
    }

    @Test
    void missingRequestParam_shouldReturn400() throws Exception {
        mockMvc.perform(get("/fake/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("q")));
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
    void resourceNotFound_shouldReturn404WithMessage() throws Exception {
        mockMvc.perform(get("/fake/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value("Product with id 42 not found"));
    }

    @Test
    void responseStatusException_shouldKeepStatusAndReason() throws Exception {
        mockMvc.perform(get("/fake/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Category already exists: Books"));
    }

    @Test
    void dataIntegrityViolation_shouldReturn409WithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/fake/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Violation d'intégrité de données"))
                .andExpect(jsonPath("$.message").value(not(containsString("users_email_key"))));
    }

    @Test
    void unknownSortProperty_shouldReturn400() throws Exception {
        mockMvc.perform(get("/fake/sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort property: foo"));
    }

    @Test
    void wrappedUnknownSortProperty_shouldReturn400() throws Exception {
        mockMvc.perform(get("/fake/wrapped-sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort property: foo"));
    }

    @Test
    void otherInvalidDataAccessApiUsage_shouldReturn500() throws Exception {
        mockMvc.perform(get("/fake/api-usage"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Une erreur inattendue est survenue"));
    }

    @Test
    void unexpectedException_shouldReturn500WithoutItsMessage() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
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

        @GetMapping("/items/{id}")
        Long findById(@PathVariable Long id) {
            return id;
        }

        @GetMapping("/search")
        String search(@RequestParam String q) {
            return q;
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Product", 42L);
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists: Books");
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("duplicate key value violates unique constraint \"users_email_key\""));
        }

        @GetMapping("/sort")
        void sort() {
            throw invalidSort();
        }

        @GetMapping("/wrapped-sort")
        void wrappedSort() {
            throw new InvalidDataAccessApiUsageException("No property 'foo' found", invalidSort());
        }

        @GetMapping("/api-usage")
        void apiUsage() {
            throw new InvalidDataAccessApiUsageException("something else");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("connection string with password=secret");
        }

        private PropertyReferenceException invalidSort() {
            return new PropertyReferenceException("foo", TypeInformation.of(Product.class), List.of());
        }
    }
}
