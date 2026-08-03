package com.expensewise.recurring.schedule;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Advances by one year, targeting the rule's original start-date month/day
 * (clamped to that target month's length — handles a Feb 29 start rolling
 * into a non-leap year).
 */
@Component
public class YearlyNextDueDateCalculator implements NextDueDateCalculator {

    @Override
    public String frequency() {
        return "YEARLY";
    }

    @Override
    public LocalDate nextDueDate(LocalDate currentDueDate, LocalDate startDate) {
        int targetYear = currentDueDate.getYear() + 1;
        YearMonth targetMonth = YearMonth.of(targetYear, startDate.getMonthValue());
        int targetDay = Math.min(startDate.getDayOfMonth(), targetMonth.lengthOfMonth());
        return LocalDate.of(targetYear, startDate.getMonthValue(), targetDay);
    }
}
