package com.memespeak.guardrail.abuse;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads and provides the profanity/abuse word dictionary.
 *
 * <p>The dictionary is loaded once at startup from the configured classpath
 * resource. It is immutable after loading — thread-safe for concurrent reads.
 *
 * <p>Words in the dictionary are the NORMALIZED forms produced by
 * {@link com.memespeak.util.TextNormalizer#normalizeForSafety(String)}.
 * Do not add words with special characters or uppercase — they will not match.
 *
 * <p>To expand the dictionary: add words to the text file.
 * No code changes, no restart needed if using an external file.
 */
@Slf4j
@Component
public class ProfanityDictionary {

    @Value("${memespeak.guardrail.profanity-config:classpath:guardrail/profanity.txt}")
    private Resource dictionaryResource;

    private Set<String> words;

    @PostConstruct
    void load() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dictionaryResource.getInputStream(), StandardCharsets.UTF_8))) {

            words = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());

            log.info("Profanity dictionary loaded: {} entries", words.size());
        } catch (Exception e) {
            log.error("Failed to load profanity dictionary from {}: {}", dictionaryResource, e.getMessage());
            words = Collections.emptySet();
        }
    }

    /**
     * Returns the set of normalized profanity words.
     * The returned set is unmodifiable and thread-safe.
     */
    public Set<String> getWords() {
        return words;
    }
}
