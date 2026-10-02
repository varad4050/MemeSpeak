package com.memespeak.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Incoming translation request.
 *
 * <p>The text may contain Gen-Z slang, emoji, abbreviations, meme references,
 * or any mixture of informal internet language. Max length is bounded to
 * prevent abuse and control LLM token costs.
 */
@Schema(description = "Translation request payload")
public record TranslationRequest(

        @Schema(
                description = "The text containing internet slang or Gen-Z expressions to translate",
                example = "Bro is absolutely cooked 💀",
                minLength = 1,
                maxLength = 500
        )
        @NotBlank(message = "text must not be blank")
        @Size(min = 1, max = 500, message = "text must be between 1 and 500 characters")
        String text

) {}
