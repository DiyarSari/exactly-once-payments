package com.diyarsari.transfer.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps every error to an RFC 7807 {@link ProblemDetail}. Spring's base class already covers
 * framework errors (missing header, malformed JSON, ...); this adds the domain ones.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({AccountNotFoundException.class, TransferNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail handleKeyReused(IdempotencyKeyReusedException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused", ex.getMessage(),
                "IDEMPOTENCY_KEY_REUSED");
    }

    @ExceptionHandler(TransferConflictException.class)
    ProblemDetail handleConflict(TransferConflictException ex) {
        log.warn(ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Concurrent modification", ex.getMessage(), "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred",
                "INTERNAL_ERROR");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request contains invalid fields", "VALIDATION_FAILED");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NonNull HandlerMethodValidationException ex,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        // Simple parameters such as headers and query params...
        ex.getValueResults().forEach(result -> {
            String name = parameterName(result.getMethodParameter());
            result.getResolvableErrors().forEach(e -> errors.putIfAbsent(name, e.getDefaultMessage()));
        });
        // ...and @Valid request bodies, reported per field.
        ex.getBeanResults().forEach(result -> {
            result.getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
            result.getGlobalErrors().forEach(e -> errors.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        });
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request contains invalid parameters", "VALIDATION_FAILED");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Gives framework-generated problems (missing header, bad JSON, ...) an errorCode too, for a uniform shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("errorCode"))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty("errorCode", status == null ? "ERROR" : status.name());
        }
        return response;
    }

    private static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) {
            return header.value();
        }
        return parameter.getParameterName() == null ? "parameter" : parameter.getParameterName();
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        return problem;
    }
}
