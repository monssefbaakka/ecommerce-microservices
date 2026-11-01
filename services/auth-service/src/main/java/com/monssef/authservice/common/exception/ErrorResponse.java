package com.monssef.authservice.common.exception;

import java.util.List;

// Same error format as the monolith: a client handles the errors of every service the same way
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        List<FieldError> errors
) {

    public record FieldError(String field, String message) {}
}
