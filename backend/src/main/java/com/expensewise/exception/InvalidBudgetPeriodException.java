package com.expensewise.exception;

public class InvalidBudgetPeriodException extends RuntimeException {

    public InvalidBudgetPeriodException(String message) {
        super(message);
    }
}
