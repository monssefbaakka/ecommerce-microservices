package com.monssef.ecommerce.common.exception;

import java.util.List;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.monssef.ecommerce.common.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns every exception thrown by a controller into the same ErrorResponse JSON.
 * The parent class already maps the Spring MVC exceptions to the right status
 * (malformed JSON 400, wrong type in the URL 400, 405, 415, unknown URL 404...):
 * handleExceptionInternal only changes the body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Erreurs de validation @Valid (400 Bad Request) avec détail par champ.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                "Un ou plusieurs champs sont invalides",
                extractPath(request),
                fieldErrors
        );
        return handleExceptionInternal(ex, error, headers, status, request);
    }

    /**
     * Ressource introuvable (404 Not Found).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not found",
                ex.getMessage(),
                extractPath(request),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Exceptions métier avec statut HTTP explicite (409 Conflict par exemple).
     * More specific than the ErrorResponseException handler of the parent class, which would drop the reason.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, WebRequest request) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(), request);
    }

    /**
     * Violation d'intégrité de données (contrainte unique, clé étrangère...) -> 409.
     * The database message names tables and values: logged, never sent to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {

        log.warn("Data integrity violation on {}: {}", extractPath(request), ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "Violation d'intégrité de données", request);
    }

    /**
     * ?sort=foo on a paged endpoint: Spring Data rejects the unknown property -> 400.
     * Depending on the repository, it arrives alone or wrapped in InvalidDataAccessApiUsageException.
     */
    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ResponseEntity<ErrorResponse> handleInvalidSort(RuntimeException ex, WebRequest request) {
        if (NestedExceptionUtils.getMostSpecificCause(ex) instanceof PropertyReferenceException invalid) {
            return error(HttpStatus.BAD_REQUEST, "Invalid sort property: " + invalid.getPropertyName(), request);
        }
        return handleGenericException(ex, request);
    }

    /**
     * Toute autre exception non gérée -> 500 Internal Server Error.
     * The stack trace goes to the logs only: its message may reveal internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error on {}", extractPath(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error",
                "Une erreur inattendue est survenue",
                extractPath(request),
                List.of()
        ));
    }

    // Every exception handled by the parent class ends here, with a ProblemDetail body:
    // replaced by an ErrorResponse, keeping the status and headers (e.g. Allow on a 405)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (statusCode.is5xxServerError()) {
            log.error("Server error on {}", extractPath(request), ex);
        }
        if (!(body instanceof ErrorResponse)) {
            String detail = body instanceof ProblemDetail problem ? problem.getDetail() : null;
            body = new ErrorResponse(
                    statusCode.value(),
                    reasonPhrase(statusCode),
                    detail != null ? detail : reasonPhrase(statusCode),
                    extractPath(request),
                    List.of()
            );
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, WebRequest request) {
        ErrorResponse error = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                extractPath(request),
                List.of()
        );
        return ResponseEntity.status(status).body(error);
    }

    private String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : String.valueOf(statusCode.value());
    }

    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : description;
    }
}
