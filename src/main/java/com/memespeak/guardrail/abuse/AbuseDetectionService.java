package com.memespeak.guardrail.abuse;

import com.memespeak.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Determines whether a user's input is safe to send to the LLM.
 *
 * <p>Detection pipeline (V1 — deterministic layer):
 * <ol>
 *   <li>Normalize the input aggressively to defeat common obfuscation.</li>
 *   <li>Check the normalized text for known profanity/abuse words using
 *       whole-word and substring matching against the loaded dictionary.</li>
 * </ol>
 *
 * <p>Architecture note: The service is designed for extension.
 * A future semantic/ML classification layer can be plugged in here
 * (e.g., a moderation API call) without changing the calling code.
 * The {@link SafetyDecision} return type already carries the reason
 * needed for metrics and logging.
 *
 * <p>This service does NOT call the LLM. The LLM is never invoked
 * until after the safety check returns {@link SafetyDecision#allow()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbuseDetectionService {

    private final ProfanityDictionary dictionary;
    private final TextNormalizer normalizer;

    /**
     * Evaluates the raw user input against the abuse policy.
     *
     * @param rawInput the raw text as submitted by the user
     * @return a {@link SafetyDecision} — never null
     */
    public SafetyDecision evaluate(String rawInput) {
        String normalized = normalizer.normalizeForSafety(rawInput);

        for (String bannedWord : dictionary.getWords()) {
            if (containsWord(normalized, bannedWord)) {
                log.debug("Safety check blocked: matched pattern '{}'", bannedWord);
                return SafetyDecision.block("PROFANITY:" + bannedWord);
            }
        }

        return SafetyDecision.allow();
    }

    /**
     * Checks whether {@code text} contains {@code word} as a substring.
     *
     * <p>Since both the text and the dictionary have been normalized
     * (all non-alphanumeric removed), this is a simple substring check.
     * Both inputs are already lowercase — case comparison is safe.
     */
    private boolean containsWord(String normalizedText, String word) {
        return normalizedText.contains(word);
    }
}
