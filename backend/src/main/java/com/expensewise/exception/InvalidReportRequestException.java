package com.expensewise.exception;

/**
 * Covers every bad-input case on a report request (unknown type, unknown
 * format, month/year out of range) — one class rather than one exception
 * per field, since they're all the same "this query parameter is invalid"
 * shape. Carries the offending field name so GlobalExceptionHandler can
 * still report a precise fieldErrors entry.
 */
public class InvalidReportRequestException extends RuntimeException {

    private final String field;

    public InvalidReportRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
