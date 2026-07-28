package com.expensewise.exception;

/**
 * Thrown when a budget's category is not visible to the caller (a system
 * category is fine, but another user's private category is not) or when
 * the category is not an EXPENSE category — budgets are spending limits,
 * not income targets.
 */
public class InvalidBudgetCategoryException extends RuntimeException {

    public InvalidBudgetCategoryException(String message) {
        super(message);
    }
}
