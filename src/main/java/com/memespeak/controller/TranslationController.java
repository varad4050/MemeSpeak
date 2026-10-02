package com.memespeak.controller;

import com.memespeak.dto.TranslationRequest;
import com.memespeak.dto.TranslationResponse;
import com.memespeak.dto.ErrorResponse;
import com.memespeak.service.TranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Primary REST controller for the MemeSpeak translation API.
 *
 * <p>This controller is intentionally thin. It:
 * <ul>
 *   <li>Accepts and validates the HTTP request (Spring Validation handles this).</li>
 *   <li>Delegates all business logic to {@link TranslationService}.</li>
 *   <li>Returns the service result or lets {@link com.memespeak.exception.GlobalExceptionHandler}
 *       convert exceptions into error responses.</li>
 * </ul>
 *
 * <p>Controllers must NOT contain business logic, safety checks, caching,
 * or any other domain concern.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Translation", description = "Internet slang translation API")
public class TranslationController {

    private final TranslationService translationService;

    /**
     * Translates Gen-Z / internet slang into plain English.
     *
     * <p>Requires a valid Google ID Token in the {@code Authorization: Bearer} header.
     */
    @Operation(
            summary = "Translate internet slang",
            description = "Accepts a sentence containing Gen-Z slang, emoji, or meme language and returns a plain-English explanation.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Translation successful",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TranslationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — text is blank or too long",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Google ID token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Input blocked by safety guardrail (profanity/injection)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded (30 requests/minute)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "LLM service temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(
            value = "/translate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TranslationResponse> translate(
            @Valid @RequestBody TranslationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.debug("Translation request received from user={}", jwt.getSubject());
        TranslationResponse response = translationService.translate(request, jwt);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the currently authenticated user's profile information.
     * Useful for verifying authentication is working.
     */
    @Operation(
            summary = "Get current user profile",
            description = "Returns basic profile information from the authenticated Google ID token.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponse(responseCode = "200", description = "User profile returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid Google ID token")
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(java.util.Map.of(
                "googleId", jwt.getSubject(),
                "email",    jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : "",
                "name",     jwt.getClaimAsString("name")  != null ? jwt.getClaimAsString("name")  : ""
        ));
    }
}
