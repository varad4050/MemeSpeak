package com.memespeak.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Successful translation response.
 *
 * <p>This is the only shape returned to the public API on success.
 * It intentionally contains no internal metadata (no model name,
 * no cache key, no token count, no internal IDs).
 */
@Schema(description = "Successful translation result")
public record TranslationResponse(

        @Schema(
                description = "A short, plain-English meaning of the slang in context",
                example = "He's in serious trouble."
        )
        @JsonProperty("meaning")
        String meaning,

        @Schema(
                description = "A concise contextual explanation of the slang term(s) used",
                example = "\"Cooked\" means someone is in a situation where failure or a bad outcome is very likely."
        )
        @JsonProperty("explanation")
        String explanation

) {}
