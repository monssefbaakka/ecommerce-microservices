package com.monssef.ecommerce.common.exception;

import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        List<FieldError> errors
) {

    public record FieldError(String field, String message) {}
}
