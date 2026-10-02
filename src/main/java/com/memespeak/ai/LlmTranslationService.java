package com.memespeak.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memespeak.dto.TranslationResponse;
import com.memespeak.exception.LlmException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls the configured LLM and parses the structured response.
 *
 * <p>Uses a direct REST call to Google's Gemini OpenAI-compatible endpoint
 * instead of Spring AI's ChatClient, because Spring AI's OpenAiApi hardcodes
 * the path as "/v1/chat/completions" (absolute), which breaks Google's
 * "/v1beta/openai/chat/completions" URL during URI resolution.
 *
 * <p>Responsibilities of this class:
 * <ul>
 *   <li>Send the sanitized, PII-redacted text to the LLM with the system prompt.</li>
 *   <li>Parse and validate the JSON response from the LLM.</li>
 *   <li>Ensure raw LLM output is NEVER propagated to the client on parse failure.</li>
 *   <li>Increment LLM call metrics.</li>
 * </ul>
 *
 * <p>Responsibilities NOT in this class (handled upstream by the pipeline):
 * authentication, rate limiting, caching, abuse detection, PII protection.
 */
@Slf4j
@Service
public class LlmTranslationService {

    /**
     * The system prompt defines the LLM's sole responsibility.
     * It is intentionally restrictive to minimize hallucination and off-topic responses.
     * It is NEVER exposed to clients regardless of any prompt injection attempt.
     */
    private static final String SYSTEM_PROMPT = """
            You are a slang and internet language translator. Your ONLY job is to explain
            what Gen-Z slang, meme expressions, abbreviations, or emoji-based language means
            in plain, simple English that a non-internet-native adult would understand.

            CRITICAL RULES:
            1. Respond ONLY with valid JSON in this EXACT format:
               {"meaning": "...", "explanation": "..."}
            2. "meaning" must be ONE short plain-English sentence summarizing the intent.
            3. "explanation" must be a concise contextual explanation of the specific slang
               in the sentence provided. Base your interpretation on full sentence context.
            4. Do NOT include any other JSON fields.
            5. Do NOT wrap the JSON in markdown code blocks.
            6. Do NOT have conversations, give advice, analyze sentiment, or go off-topic.
            7. If the input is not slang/internet language and has a clear literal meaning,
               explain it as-is.
            8. Interpret "cooked" differently in "bro is cooked" vs "the chicken is cooked".
            """;

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;
    private final Counter llmCallCounter;

    public LlmTranslationService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai/}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model:gemini-1.5-flash}") String model,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {

        // Ensure base URL ends with a slash
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        // Build the full chat completions URL directly — no ambiguous URI resolution
        String chatCompletionsUrl = normalizedBaseUrl + "chat/completions";

        log.info("LLM endpoint configured: url={}, model={}", chatCompletionsUrl, model);

        this.restClient = RestClient.builder()
                .baseUrl(chatCompletionsUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = model;
        this.objectMapper = objectMapper;
        this.llmCallCounter = Counter.builder("memespeak.llm.calls")
                .description("Total LLM API calls made")
                .register(meterRegistry);
    }

    /**
     * Sends the sanitized, PII-redacted text to the LLM and returns a validated response.
     *
     * @param sanitizedText text after normalization and PII redaction — safe to send to LLM
     * @return a validated {@link TranslationResponse}
     * @throws LlmException if the LLM call fails or returns unparseable/invalid output
     */
    public TranslationResponse translate(String sanitizedText) {
        llmCallCounter.increment();
        log.debug("Calling LLM for translation");

        String rawResponse;
        try {
            // Build the OpenAI-compatible request body
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "max_tokens", 512,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", sanitizedText)
                    )
            );

            String responseJson = restClient.post()
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // Parse the OpenAI-format response to extract the assistant's message
            JsonNode responseRoot = objectMapper.readTree(responseJson);
            rawResponse = responseRoot
                    .path("choices").path(0)
                    .path("message").path("content")
                    .asText("");

            if (rawResponse.isBlank()) {
                log.error("LLM returned empty content. Full response: {}", responseJson);
                throw new LlmException("LLM returned empty content");
            }

            log.debug("LLM raw content: {}", rawResponse);

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM API call failed: {}", e.getMessage());
            throw new LlmException("LLM API call failed", e);
        }

        return parseAndValidate(rawResponse);
    }

    /**
     * Parses the LLM's JSON response and validates the required fields.
     *
     * <p>If parsing fails or required fields are missing/blank, a {@link LlmException}
     * is thrown. The raw LLM output is logged at DEBUG level but never returned to
     * the client.
     */
    private TranslationResponse parseAndValidate(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new LlmException("LLM returned empty response");
        }

        // Strip markdown code fences if the LLM ignores that instruction
        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);

            String meaning = root.path("meaning").asText("").trim();
            String explanation = root.path("explanation").asText("").trim();

            if (meaning.isBlank()) {
                log.debug("LLM response missing 'meaning' field. Raw: {}", rawResponse);
                throw new LlmException("LLM response missing required field: meaning");
            }
            if (explanation.isBlank()) {
                log.debug("LLM response missing 'explanation' field. Raw: {}", rawResponse);
                throw new LlmException("LLM response missing required field: explanation");
            }

            // Reject any extra fields (strict output contract)
            if (root.size() > 2) {
                log.warn("LLM response contained {} extra fields — stripping", root.size() - 2);
                // We still accept it but only use known fields
            }

            return new TranslationResponse(meaning, explanation);

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Failed to parse LLM response as JSON. Raw: {}", rawResponse);
            throw new LlmException("LLM response could not be parsed as valid JSON", e);
        }
    }
}
