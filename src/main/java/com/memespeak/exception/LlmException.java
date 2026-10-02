package com.memespeak.exception;

/**
 * Thrown when the LLM call fails or returns an unparseable/invalid response.
 *
 * <p>Results in HTTP 503 Service Unavailable — the downstream AI service
 * is temporarily unable to fulfill the request.
 *
 * <p>Raw LLM errors or unparsed outputs are NEVER propagated to the client.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
