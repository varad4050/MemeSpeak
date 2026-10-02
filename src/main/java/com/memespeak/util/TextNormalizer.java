package com.memespeak.util;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Text normalization utilities used across the guardrail and cache pipeline.
 *
 * <p>Two normalization strategies are provided:
 * <ol>
 *   <li>{@link #normalizeForCache} — lightweight normalization used to derive
 *       stable cache keys. Preserves the meaning of the text.</li>
 *   <li>{@link #normalizeForSafety} — aggressive normalization used to defeat
 *       common obfuscation techniques before profanity/abuse checking.
 *       Does NOT preserve meaning — safety use only.</li>
 * </ol>
 *
 * <p>V1 limitations (documented):
 * <ul>
 *   <li>Leet-speak substitution is character-level only; context-aware
 *       disambiguation is a future semantic layer concern.</li>
 *   <li>Novel obfuscation patterns not in this list will not be caught.
 *       The profanity dictionary and pattern list are designed to be
 *       extended without code changes.</li>
 * </ul>
 */
@Component
public class TextNormalizer {

    /**
     * Normalizes text for consistent cache key generation.
     *
     * <p>Steps:
     * <ol>
     *   <li>Lowercase</li>
     *   <li>Strip leading/trailing whitespace</li>
     *   <li>Collapse multiple spaces into one</li>
     * </ol>
     *
     * <p>Emoji and unicode are preserved — they carry meaning for slang translation.
     */
    public String normalizeForCache(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                   .trim()
                   .replaceAll("\\s+", " ");
    }

    /**
     * Aggressively normalizes text to defeat common obfuscation used to
     * sneak profanity or abusive language past simple filters.
     *
     * <p>Steps:
     * <ol>
     *   <li>Lowercase</li>
     *   <li>Apply leet-speak character substitutions (e.g., {@code @→a})</li>
     *   <li>Remove all non-alphanumeric characters (defeats dot/space/asterisk tricks)</li>
     *   <li>Collapse 3+ consecutive identical characters to 1 (defeats stretching)</li>
     * </ol>
     *
     * <p>Examples:
     * <pre>
     *   "f u c k"  → "fuck"
     *   "f.u.c.k"  → "fuck"
     *   "fuuuuuck" → "fuck"
     *   "sh!t"     → "shit"
     *   "b1tch"    → "bitch"
     * </pre>
     *
     * <p>This output is used ONLY for profanity detection, never for display or LLM input.
     */
    public String normalizeForSafety(String text) {
        if (text == null) return "";
        String result = text.toLowerCase(Locale.ROOT);
        result = applyLeetSubstitutions(result);
        result = result.replaceAll("[^a-z0-9]", "");       // remove all non-alphanumeric
        result = result.replaceAll("(.)\\1{2,}", "$1");    // collapse 3+ repeated chars → 1
        return result;
    }

    /**
     * Applies common leet-speak character substitutions.
     *
     * <p>These substitutions are intentionally broad — we prefer a small
     * number of false positives over missing actual profanity.
     */
    private String applyLeetSubstitutions(String text) {
        return text
                .replace("@", "a")
                .replace("3", "e")
                .replace("1", "i")
                .replace("0", "o")
                .replace("$", "s")
                .replace("!", "i")
                .replace("5", "s")
                .replace("7", "t")
                .replace("|", "i")
                .replace("+", "t")
                .replace("ph", "f");
    }
}
