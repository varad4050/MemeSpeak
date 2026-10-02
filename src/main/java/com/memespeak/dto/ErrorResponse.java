package com.memespeak.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standardized error response shape returned for all API errors.
 *
 * <p>Never exposes internal details: no stack traces, no internal IDs,
 * no database errors, no model names.
 *
 * <p>The {@code details} field is optional and only populated when the error
 * has structured sub-information worth surfacing (e.g., field validation errors).
 */
@Schema(description = "API error response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        @Schema(description = "Machine-readable error code", example = "UNSAFE_INPUT")
        String error,

        @Schema(description = "Human-readable error message", example = "Please keep your request respectful.")
        String message,

        @Schema(description = "Optional structured details (e.g., validation field errors)")
        Object details

) {
    /** Convenience constructor without details. */
    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }
}
