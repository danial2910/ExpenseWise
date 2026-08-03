package com.expensewise.exception;

/**
 * A receipt upload that isn't an allowed file type (JPG/PNG/WEBP/PDF) or
 * exceeds the size limit. Mapped to a 400 VALIDATION_FAILED on the "file"
 * field. Mirrors InvalidAvatarException.
 */
public class InvalidReceiptException extends RuntimeException {

    public InvalidReceiptException(String message) {
        super(message);
    }
}
