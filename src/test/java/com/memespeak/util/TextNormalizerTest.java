package com.memespeak.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextNormalizer}.
 *
 * <p>No Spring context required — pure unit test.
 */
class TextNormalizerTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    // -------------------------------------------------------------------------
    // normalizeForCache
    // -------------------------------------------------------------------------

    @Test
    void normalizeForCache_lowercasesText() {
        assertThat(normalizer.normalizeForCache("What Does RIZZ Mean?"))
                .isEqualTo("what does rizz mean?");
    }

    @Test
    void normalizeForCache_trimsWhitespace() {
        assertThat(normalizer.normalizeForCache("  bro is cooked  "))
                .isEqualTo("bro is cooked");
    }

    @Test
    void normalizeForCache_collapsesMultipleSpaces() {
        assertThat(normalizer.normalizeForCache("bro   is    cooked"))
                .isEqualTo("bro is cooked");
    }

    @Test
    void normalizeForCache_preservesEmoji() {
        assertThat(normalizer.normalizeForCache("Bro is cooked 💀"))
                .isEqualTo("bro is cooked 💀");
    }

    @Test
    void normalizeForCache_handlesNull() {
        assertThat(normalizer.normalizeForCache(null)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // normalizeForSafety
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "normalizeForSafety({0}) produces non-empty result")
    @CsvSource({
            "f u c k",        // space-separated
            "f.u.c.k",        // dot-separated
            "fuuuuuck",       // stretched
            "sh!t",           // leet
            "b1tch",          // leet digit
            "f*ck",           // asterisk removed
            "FUCK"            // uppercase
    })
    void normalizeForSafety_defeatsCommonObfuscation(String input) {
        String normalized = normalizer.normalizeForSafety(input);
        // Normalization should produce a non-empty result
        assertThat(normalized).isNotEmpty();
        // Specific assertions for each form are in dedicated tests below
    }

    @Test
    void normalizeForSafety_decodesDotSeparated() {
        // f.u.c.k → (leet) → fuck → (remove dots) → fuck
        assertThat(normalizer.normalizeForSafety("f.u.c.k")).isEqualTo("fuck");
    }

    @Test
    void normalizeForSafety_decodeSpaceSeparated() {
        assertThat(normalizer.normalizeForSafety("f u c k")).isEqualTo("fuck");
    }

    @Test
    void normalizeForSafety_collapsesStre_tchedChars() {
        assertThat(normalizer.normalizeForSafety("fuuuuuck")).isEqualTo("fuck");
    }

    @Test
    void normalizeForSafety_appliesLeetSubstitution_bangForI() {
        assertThat(normalizer.normalizeForSafety("sh!t")).isEqualTo("shit");
    }

    @Test
    void normalizeForSafety_appliesLeetSubstitution_oneForI() {
        assertThat(normalizer.normalizeForSafety("b1tch")).isEqualTo("bitch");
    }

    @Test
    void normalizeForSafety_handlesNull() {
        assertThat(normalizer.normalizeForSafety(null)).isEmpty();
    }

    @Test
    void normalizeForSafety_normalSlangPassesThrough() {
        // "rizz" should not be mangled
        String result = normalizer.normalizeForSafety("what does rizz mean");
        assertThat(result).contains("rizz");
    }
}
