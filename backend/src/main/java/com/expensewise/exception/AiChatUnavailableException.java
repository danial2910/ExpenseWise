package com.expensewise.exception;

/**
 * Thrown when the Groq call fails or returns something unusable (network
 * error, non-2xx, empty choices). Never leaks the underlying cause message
 * to the client — see CLAUDE.md's "never return stack traces or internal
 * exception messages" rule.
 */
public class AiChatUnavailableException extends RuntimeException {

    public AiChatUnavailableException(String message) {
        super(message);
    }
}
