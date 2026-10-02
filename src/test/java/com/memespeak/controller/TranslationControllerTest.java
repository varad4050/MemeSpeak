package com.memespeak.controller;

import com.memespeak.controller.TranslationController;
import com.memespeak.dto.TranslationRequest;
import com.memespeak.dto.TranslationResponse;
import com.memespeak.exception.GlobalExceptionHandler;
import com.memespeak.exception.RateLimitException;
import com.memespeak.exception.SafetyException;
import com.memespeak.security.SecurityConfig;
import com.memespeak.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for {@link TranslationController}.
 *
 * <p>Uses {@code @WebMvcTest} to test only the web layer (controller,
 * exception handler, security config). The service layer is mocked.
 *
 * <p>JWT authentication is simulated using Spring Security Test's
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} — no real Google
 * token validation occurs in tests.
 */
@WebMvcTest(TranslationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TranslationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockBean
    private TranslationService translationService;

    // Required so Spring Security doesn't try to fetch Google's JWKS in tests
    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String TRANSLATE_URL = "/api/v1/translate";

    // -------------------------------------------------------------------------
    // 200 — Success
    // -------------------------------------------------------------------------

    @Test
    void translate_returnsTranslationOnSuccess() throws Exception {
        TranslationResponse mockResponse = new TranslationResponse(
                "He's in serious trouble.",
                "\"Cooked\" means someone is in a very bad situation."
        );
        when(translationService.translate(any(), any(Jwt.class))).thenReturn(mockResponse);

        mockMvc.perform(post(TRANSLATE_URL)
                        .with(jwt().jwt(j -> j.subject("google-user-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TranslationRequest("Bro is cooked 💀"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meaning").value("He's in serious trouble."))
                .andExpect(jsonPath("$.explanation").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // 400 — Validation
    // -------------------------------------------------------------------------

    @Test
    void translate_returns400WhenTextIsBlank() throws Exception {
        mockMvc.perform(post(TRANSLATE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(translationService, never()).translate(any(), any());
    }

    @Test
    void translate_returns400WhenTextIsMissing() throws Exception {
        mockMvc.perform(post(TRANSLATE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void translate_returns400WhenTextExceedsMaxLength() throws Exception {
        String tooLong = "a".repeat(501);
        mockMvc.perform(post(TRANSLATE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TranslationRequest(tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // 401 — Unauthorized
    // -------------------------------------------------------------------------

    @Test
    void translate_returns401WhenNoToken() throws Exception {
        mockMvc.perform(post(TRANSLATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TranslationRequest("what does rizz mean"))))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 422 — Safety guardrail
    // -------------------------------------------------------------------------

    @Test
    void translate_returns422WhenSafetyBlocks() throws Exception {
        when(translationService.translate(any(), any(Jwt.class)))
                .thenThrow(new SafetyException("PROFANITY:fuck"));

        mockMvc.perform(post(TRANSLATE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TranslationRequest("What the fuck does rizz mean?"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNSAFE_INPUT"))
                .andExpect(jsonPath("$.message").value("Please keep your request respectful."));
    }

    // -------------------------------------------------------------------------
    // 429 — Rate limit
    // -------------------------------------------------------------------------

    @Test
    void translate_returns429WhenRateLimitExceeded() throws Exception {
        when(translationService.translate(any(), any(Jwt.class)))
                .thenThrow(new RateLimitException());

        mockMvc.perform(post(TRANSLATE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TranslationRequest("what does rizz mean"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    // -------------------------------------------------------------------------
    // Safety verification: LLM not called
    // -------------------------------------------------------------------------

    @Test
    void translate_doesNotCallServiceForBlankInput() throws Exception {
        mockMvc.perform(post(TRANSLATE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \"}"))
                .andExpect(status().isBadRequest());

        verify(translationService, never()).translate(any(), any());
    }
}
