package com.expensewise.exception;

/**
 * Thrown when a transaction's category is not visible to the caller (a
 * system category is fine, but another user's private category is not) or
 * when the transaction's type doesn't match the category's type.
 */
public class InvalidTransactionCategoryException extends RuntimeException {

    public InvalidTransactionCategoryException(String message) {
        super(message);
    }
}
