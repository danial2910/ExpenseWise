package com.expensewise.recurring.schedule;

import java.time.LocalDate;

/**
 * Strategy for advancing a recurring rule's next due date by one period.
 * One implementation per frequency (WEEKLY/MONTHLY/YEARLY) — see
 * RecurringGenerationService, which resolves the right one by
 * {@link #frequency()}.
 */
public interface NextDueDateCalculator {

    /** The {@code recurring_rules.frequency} value this calculator handles. */
    String frequency();

    /**
     * Advances {@code currentDueDate} by one period. {@code startDate} is
     * the rule's original anchor (day-of-month for MONTHLY, month+day for
     * YEARLY) so a month-end date (e.g. Jan 31) correctly returns to day 31
     * in a later month that has one, instead of staying clamped at 28/30
     * forever.
     */
    LocalDate nextDueDate(LocalDate currentDueDate, LocalDate startDate);
}
