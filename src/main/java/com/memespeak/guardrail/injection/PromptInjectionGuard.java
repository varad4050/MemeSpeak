package com.memespeak.guardrail.injection;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Detects and blocks prompt injection attempts before they reach the LLM.
 *
 * <p>Because MemeSpeak has a deliberately narrow purpose (slang translation),
 * inputs attempting to redirect, override, or manipulate the model's behavior
 * are clearly out of scope and are blocked as a hard policy.
 *
 * <p>Detection strategy (V1 — deterministic):
 * <ul>
 *   <li>Lowercase and trim the input.</li>
 *   <li>Check for configured injection phrases as substrings.</li>
 * </ul>
 *
 * <p>The phrase list is loaded from a classpath resource and is expandable
 * without code changes.
 *
 * <p>The application does NOT expose system prompts, instructions, API keys,
 * or internal architecture — regardless of what the LLM might return.
 * This guard ensures such inputs never reach the LLM in the first place.
 */
@Slf4j
@Service
public class PromptInjectionGuard {

    @Value("${memespeak.guardrail.injection-config:classpath:guardrail/injection-patterns.txt}")
    private Resource patternsResource;

    private List<String> patterns;

    @PostConstruct
    void load() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(patternsResource.getInputStream(), StandardCharsets.UTF_8))) {

            patterns = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableList());

            log.info("Prompt injection guard loaded: {} patterns", patterns.size());
        } catch (Exception e) {
            log.error("Failed to load injection patterns: {}", e.getMessage());
            patterns = Collections.emptyList();
        }
    }

    /**
     * Returns {@code true} if the input appears to be a prompt injection attempt.
     *
     * @param input the raw user input (before any other normalization)
     */
    public boolean isInjectionAttempt(String input) {
        if (input == null || input.isBlank()) return false;
        String lowered = input.toLowerCase(Locale.ROOT);
        return patterns.stream().anyMatch(lowered::contains);
    }
}
