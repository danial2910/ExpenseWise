package com.expensewise.exception;

/**
 * Thrown when creating or updating a category budget for a month that has
 * no overall budget yet — category budgets are capped by (and therefore
 * require) an overall budget to exist for the same month.
 */
public class OverallBudgetRequiredException extends RuntimeException {

    public OverallBudgetRequiredException(String message) {
        super(message);
    }
}
