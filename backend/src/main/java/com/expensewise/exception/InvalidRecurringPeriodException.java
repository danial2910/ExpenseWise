package com.expensewise.exception;

/**
 * Thrown when a recurring rule's end date is before its start date.
 * Mirrors InvalidBudgetPeriodException.
 */
public class InvalidRecurringPeriodException extends RuntimeException {

    public InvalidRecurringPeriodException(String message) {
        super(message);
    }
}
