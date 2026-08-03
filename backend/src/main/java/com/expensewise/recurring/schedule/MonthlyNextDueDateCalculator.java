package com.expensewise.recurring.schedule;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Advances by one calendar month, always targeting the rule's original
 * start-date day-of-month (clamped to the target month's length). Using the
 * start date's day as the anchor — rather than the previous due date's day —
 * is what makes a Jan 31 rule correctly return to day 31 in March (after
 * being clamped to Feb 28), instead of drifting to day 28 permanently.
 */
@Component
public class MonthlyNextDueDateCalculator implements NextDueDateCalculator {

    @Override
    public String frequency() {
        return "MONTHLY";
    }

    @Override
    public LocalDate nextDueDate(LocalDate currentDueDate, LocalDate startDate) {
        LocalDate advanced = currentDueDate.plusMonths(1);
        int targetDay = Math.min(startDate.getDayOfMonth(), advanced.lengthOfMonth());
        return advanced.withDayOfMonth(targetDay);
    }
}
