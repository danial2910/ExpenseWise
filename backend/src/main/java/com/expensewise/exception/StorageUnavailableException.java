package com.expensewise.exception;

/**
 * Thrown when a call to the object store fails (network error, non-2xx).
 * Never leaks the underlying cause message to the client — see CLAUDE.md's
 * "never return stack traces or internal exception messages" rule.
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }
}
