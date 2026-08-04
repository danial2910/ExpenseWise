package com.expensewise.exception;

/**
 * Thrown when the NewsData.io call fails or returns something unusable
 * (network error, non-2xx). Never leaks the underlying cause message to
 * the client — see CLAUDE.md's "never return stack traces or internal
 * exception messages" rule.
 */
public class NewsUnavailableException extends RuntimeException {

    public NewsUnavailableException(String message) {
        super(message);
    }
}
