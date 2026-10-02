package com.memespeak.exception;

import com.memespeak.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handling for the entire API.
 *
 * <p>All exceptions are mapped to a consistent {@link ErrorResponse} shape.
 * Internal details (stack traces, DB errors, LLM error messages) are logged
 * server-side but NEVER sent to the client.
 *
 * <p>Error code catalogue:
 * <ul>
 *   <li>{@code VALIDATION_ERROR}   — 400 — request failed bean validation</li>
 *   <li>{@code UNSAFE_INPUT}       — 422 — guardrail blocked the request</li>
 *   <li>{@code UNAUTHORIZED}       — 401 — missing or invalid auth token</li>
 *   <li>{@code RATE_LIMIT_EXCEEDED}— 429 — too many requests</li>
 *   <li>{@code AI_UNAVAILABLE}     — 503 — LLM call failed or returned bad output</li>
 *   <li>{@code INTERNAL_ERROR}     — 500 — unexpected server error</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // 400 — Validation
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    // -------------------------------------------------------------------------
    // 401 — Authentication
    // -------------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        // Do not log the full exception — it may contain token details
        log.debug("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Authentication required. Provide a valid Google ID token."));
    }

    // -------------------------------------------------------------------------
    // 422 — Safety guardrail
    // -------------------------------------------------------------------------

    @ExceptionHandler(SafetyException.class)
    public ResponseEntity<ErrorResponse> handleSafety(SafetyException ex) {
        // Log the reason code but not the user's raw input (may be sensitive)
        log.info("Request blocked by safety guardrail: reason={}", ex.getReason());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("UNSAFE_INPUT", "Please keep your request respectful."));
    }

    // -------------------------------------------------------------------------
    // 429 — Rate limit
    // -------------------------------------------------------------------------

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex) {
        log.info("Rate limit exceeded for request");
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("RATE_LIMIT_EXCEEDED", "Too many requests. Please try again in a minute."));
    }

    // -------------------------------------------------------------------------
    // 503 — LLM failure
    // -------------------------------------------------------------------------

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ErrorResponse> handleLlm(LlmException ex) {
        // Log the internal cause for debugging — never expose to client
        log.error("LLM service error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("AI_UNAVAILABLE", "The translation service is temporarily unavailable. Please try again later."));
    }

    // -------------------------------------------------------------------------
    // 500 — Catch-all
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
