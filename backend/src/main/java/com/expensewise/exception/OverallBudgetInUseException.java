package com.expensewise.exception;

/**
 * Thrown when deleting the overall budget while category budgets still
 * exist for that month — deleting it first would leave category budgets
 * that no longer have the overall limit they're capped against. Mirrors
 * CategoryInUseException's 409 CONFLICT convention.
 */
public class OverallBudgetInUseException extends RuntimeException {

    public OverallBudgetInUseException(String message) {
        super(message);
    }
}
