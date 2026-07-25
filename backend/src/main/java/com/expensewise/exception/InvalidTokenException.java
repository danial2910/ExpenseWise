package com.expensewise.exception;

/**
 * A reset or refresh token that is missing, expired, already used, or
 * already revoked. Deliberately generic so the response never reveals
 * which of those specifically applies.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
