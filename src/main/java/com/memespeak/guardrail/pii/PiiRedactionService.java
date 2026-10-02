package com.memespeak.guardrail.pii;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Detects and redacts Personally Identifiable Information (PII) from
 * text before it is sent to the LLM.
 *
 * <p>The LLM should never receive raw PII — it is a third-party service
 * and unnecessary personal data exposure increases privacy risk.
 *
 * <p>Redaction categories (V1):
 * <ul>
 *   <li>Email addresses          → {@code [EMAIL]}</li>
 *   <li>Phone numbers (10+ digits) → {@code [PHONE]}</li>
 *   <li>Credit/debit card numbers → {@code [CARD]}</li>
 *   <li>Numeric API key patterns   → {@code [CREDENTIAL]}</li>
 * </ul>
 *
 * <p>Names and general identifiers are NOT automatically redacted in V1
 * because they are ambiguous (e.g., "Bro" is slang, not a name). A future
 * NER layer can be added here without changing the calling pipeline.
 *
 * <p>Redaction is applied on the SANITIZED text, not the raw input.
 * The original text is never stored.
 */
@Service
public class PiiRedactionService {

    // Email: standard RFC-5321 simplified
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    // Phone: 10–15 consecutive digits, optionally separated by spaces/dashes/dots
    // Matches: 9876543210, +91-9876543210, 987 654 3210
    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+\\d{1,3}[\\s\\-]?)?\\b\\d[\\d\\s\\-.]{8,}\\d\\b"
    );

    // Credit/debit card: 13–19 digits, optionally space/dash separated in groups
    private static final Pattern CARD = Pattern.compile(
            "\\b(?:\\d[ \\-]?){13,19}\\b"
    );

    // Long alphanumeric strings that resemble API keys / tokens (32+ chars, mixed case + digits)
    private static final Pattern API_KEY = Pattern.compile(
            "\\b[A-Za-z0-9_\\-]{32,}\\b"
    );

    /**
     * Redacts all detected PII from the input text.
     *
     * <p>Order matters: longer patterns (CARD) are applied before shorter
     * patterns (PHONE) to avoid partial matches.
     *
     * @param text the text to sanitize (may be the already cache-normalized text)
     * @return the text with PII replaced by placeholder tokens
     */
    public String redact(String text) {
        if (text == null || text.isBlank()) return text;

        String result = text;
        result = EMAIL.matcher(result).replaceAll("[EMAIL]");
        result = CARD.matcher(result).replaceAll("[CARD]");
        result = PHONE.matcher(result).replaceAll("[PHONE]");
        result = API_KEY.matcher(result).replaceAll("[CREDENTIAL]");

        return result;
    }
}
