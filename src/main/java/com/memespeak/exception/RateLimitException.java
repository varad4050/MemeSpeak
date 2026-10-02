package com.memespeak.exception;

/**
 * Thrown when a user exceeds their allowed translation request rate.
 *
 * <p>Results in HTTP 429 Too Many Requests.
 * The LLM is NEVER called when this exception is thrown.
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException() {
        super("Rate limit exceeded");
    }
}
