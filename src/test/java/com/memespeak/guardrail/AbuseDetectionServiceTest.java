package com.memespeak.guardrail;

import com.memespeak.guardrail.abuse.AbuseDetectionService;
import com.memespeak.guardrail.abuse.ProfanityDictionary;
import com.memespeak.guardrail.abuse.SafetyDecision;
import com.memespeak.util.TextNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AbuseDetectionService}.
 *
 * <p>Uses a real {@link ProfanityDictionary} loaded from the classpath resource
 * to verify end-to-end detection behavior.
 *
 * <p>These tests verify the requirements from the product spec:
 * <ul>
 *   <li>"What does rizz mean?" → ALLOW</li>
 *   <li>"What the fuck does rizz mean?" → BLOCK</li>
 *   <li>Obfuscated profanity → BLOCK</li>
 * </ul>
 */
@SpringBootTest(
        classes = {AbuseDetectionService.class, ProfanityDictionary.class, TextNormalizer.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class AbuseDetectionServiceTest {

    @Autowired
    private AbuseDetectionService abuseDetector;

    // -------------------------------------------------------------------------
    // ALLOW cases
    // -------------------------------------------------------------------------

    @Test
    void allowsCleanSlangQuery() {
        SafetyDecision decision = abuseDetector.evaluate("What does rizz mean?");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.isBlocked()).isFalse();
    }

    @Test
    void allowsEmojiSlang() {
        SafetyDecision decision = abuseDetector.evaluate("Bro is absolutely cooked 💀");
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsNormalSentence() {
        SafetyDecision decision = abuseDetector.evaluate("That presentation cooked me");
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsAbbreviations() {
        SafetyDecision decision = abuseDetector.evaluate("NGL this lowkey slaps");
        assertThat(decision.allowed()).isTrue();
    }

    // -------------------------------------------------------------------------
    // BLOCK cases — direct profanity
    // -------------------------------------------------------------------------

    @Test
    void blocksExplicitProfanity_inQuestion() {
        SafetyDecision decision = abuseDetector.evaluate("What the fuck does rizz mean?");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).startsWith("PROFANITY:");
    }

    @Test
    void blocksExplicitProfanity_standalone() {
        SafetyDecision decision = abuseDetector.evaluate("this is shit");
        assertThat(decision.isBlocked()).isTrue();
    }

    // -------------------------------------------------------------------------
    // BLOCK cases — obfuscated profanity
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "should block obfuscated: {0}")
    @ValueSource(strings = {
            "f u c k",         // space-separated
            "f.u.c.k",         // dot-separated
            "fuuuuuck",        // stretched
            "sh!t",            // leet
            "b1tch",           // leet digit
            "FUCK",            // uppercase
    })
    void blocksObfuscatedProfanity(String obfuscated) {
        SafetyDecision decision = abuseDetector.evaluate("What does " + obfuscated + " mean?");
        assertThat(decision.isBlocked())
                .as("Expected '%s' to be blocked", obfuscated)
                .isTrue();
    }
}
