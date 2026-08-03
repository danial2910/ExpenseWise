package com.expensewise.exception;

/**
 * Thrown when a recurring rule's category is not visible to the caller (a
 * system category is fine, but another user's private category is not) or
 * when the rule's type doesn't match the category's type. Mirrors
 * InvalidTransactionCategoryException.
 */
public class InvalidRecurringCategoryException extends RuntimeException {

    public InvalidRecurringCategoryException(String message) {
        super(message);
    }
}
