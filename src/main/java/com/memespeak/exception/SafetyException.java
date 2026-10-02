package com.memespeak.exception;

/**
 * Thrown when the safety guardrail blocks a request due to abusive language,
 * profanity, prompt injection, or other policy violations.
 *
 * <p>Results in HTTP 422 Unprocessable Entity — the request was syntactically
 * valid but violated application policy.
 *
 * <p>The LLM is NEVER called before this exception is thrown.
 */
public class SafetyException extends RuntimeException {

    private final String reason;

    public SafetyException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
