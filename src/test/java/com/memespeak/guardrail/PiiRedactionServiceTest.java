package com.memespeak.guardrail;

import com.memespeak.guardrail.pii.PiiRedactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PiiRedactionService}.
 *
 * <p>Verifies that PII categories are correctly redacted before LLM calls.
 * Key spec requirement: "My number is 9876543210, what does rizz mean?"
 * must result in "[PHONE]" — the real number must NOT reach the LLM.
 */
class PiiRedactionServiceTest {

    private PiiRedactionService piiService;

    @BeforeEach
    void setUp() {
        piiService = new PiiRedactionService();
    }

    @Test
    void redactsEmailAddress() {
        String result = piiService.redact("Email me at john@example.com about rizz");
        assertThat(result)
                .contains("[EMAIL]")
                .doesNotContain("john@example.com");
    }

    @Test
    void redactsPhoneNumber_tenDigits() {
        String result = piiService.redact("My number is 9876543210, what does rizz mean?");
        assertThat(result)
                .contains("[PHONE]")
                .doesNotContain("9876543210");
    }

    @Test
    void redactsPhoneNumber_withCountryCode() {
        String result = piiService.redact("Call me at +91 9876543210");
        assertThat(result).doesNotContain("9876543210");
    }

    @Test
    void redactsLongAlphanumericCredential() {
        String result = piiService.redact("My API key is sk-abcdefghij1234567890abcdefghijkl what does slay mean");
        assertThat(result)
                .contains("[CREDENTIAL]")
                .doesNotContain("sk-abcdefghij");
    }

    @Test
    void preservesNormalSlang() {
        String input = "Bro is cooked 💀 ngl";
        assertThat(piiService.redact(input)).isEqualTo(input);
    }

    @Test
    void handlesNullGracefully() {
        assertThat(piiService.redact(null)).isNull();
    }

    @Test
    void handlesBlankInput() {
        assertThat(piiService.redact("   ")).isEqualTo("   ");
    }

    @Test
    void redactsMultiplePiiTypesInOneSentence() {
        String input = "Hi, I'm john@example.com, call me at 9876543210";
        String result = piiService.redact(input);
        assertThat(result)
                .contains("[EMAIL]")
                .doesNotContain("john@example.com")
                .doesNotContain("9876543210");
    }
}
