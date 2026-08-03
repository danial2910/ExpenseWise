package com.expensewise.recurring.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NextDueDateCalculatorTest {

    @Test
    void weeklyAdvancesBySevenDays() {
        WeeklyNextDueDateCalculator calculator = new WeeklyNextDueDateCalculator();

        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5));

        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 12));
    }

    @Test
    void monthlyAdvancesToTheSameDayOfMonth() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();

        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 1, 15));

        assertThat(next).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    void monthlyClampsAMonthEndStartDateToTheShorterMonthsLastDay() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();

        // Jan 31 -> Feb 28 (2026 is not a leap year).
        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31));

        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void monthlyReturnsToTheOriginalDayOnceAMonthLongEnoughComesAround() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();
        LocalDate startDate = LocalDate.of(2026, 1, 31);

        // Feb 28 (clamped from Jan 31) -> Mar 31, recovering the original day.
        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, 2, 28), startDate);

        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void monthlyHandlesLeapFebruaryClamping() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();

        // Jan 30 -> Feb (2028 is a leap year, so Feb has 29 days).
        LocalDate next = calculator.nextDueDate(LocalDate.of(2028, 1, 30), LocalDate.of(2028, 1, 30));

        assertThat(next).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void yearlyAdvancesToTheSameMonthAndDay() {
        YearlyNextDueDateCalculator calculator = new YearlyNextDueDateCalculator();

        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, 5, 20), LocalDate.of(2020, 5, 20));

        assertThat(next).isEqualTo(LocalDate.of(2027, 5, 20));
    }

    @Test
    void yearlyClampsAFeb29StartDateInANonLeapYear() {
        YearlyNextDueDateCalculator calculator = new YearlyNextDueDateCalculator();

        // Start date Feb 29 2024 (leap); advancing into 2026 (non-leap) clamps to Feb 28.
        LocalDate next = calculator.nextDueDate(LocalDate.of(2025, 2, 28), LocalDate.of(2024, 2, 29));

        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28));
    }
}
