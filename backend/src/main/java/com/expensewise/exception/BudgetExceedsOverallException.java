package com.expensewise.exception;

/**
 * Thrown when a category budget's amount (alongside the month's other
 * category budgets) would exceed the overall monthly budget, or when the
 * overall budget is being set/edited below the sum of category budgets
 * already set for that month. Category and overall budgets are capped
 * against each other in both directions — see DECISIONS.md.
 */
public class BudgetExceedsOverallException extends RuntimeException {

    public BudgetExceedsOverallException(String message) {
        super(message);
    }
}
