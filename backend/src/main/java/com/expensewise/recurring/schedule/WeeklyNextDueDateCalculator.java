package com.expensewise.recurring.schedule;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class WeeklyNextDueDateCalculator implements NextDueDateCalculator {

    @Override
    public String frequency() {
        return "WEEKLY";
    }

    @Override
    public LocalDate nextDueDate(LocalDate currentDueDate, LocalDate startDate) {
        return currentDueDate.plusWeeks(1);
    }
}
