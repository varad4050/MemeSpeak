package com.memespeak.guardrail.abuse;

/**
 * Immutable result of a safety evaluation.
 *
 * <p>Design note: this is a value type rather than a boolean so that
 * the reason for blocking can be logged and used to update metrics
 * without coupling the detection logic to the logging/metrics infrastructure.
 */
public record SafetyDecision(boolean allowed, String reason) {

    public static SafetyDecision allow() {
        return new SafetyDecision(true, null);
    }

    public static SafetyDecision block(String reason) {
        return new SafetyDecision(false, reason);
    }

    public boolean isBlocked() {
        return !allowed;
    }
}
