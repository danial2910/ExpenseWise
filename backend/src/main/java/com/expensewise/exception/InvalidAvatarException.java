package com.expensewise.exception;

/**
 * An avatar upload that isn't an allowed image type or exceeds the size
 * limit. Mapped to a 400 VALIDATION_FAILED on the "avatar" field.
 */
public class InvalidAvatarException extends RuntimeException {

    public InvalidAvatarException(String message) {
        super(message);
    }
}
