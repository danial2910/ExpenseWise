package com.expensewise.recurring.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

class NextDueDateCalculatorTest {

    @Test
    void weeklyAdvancesBySevenDays() {
        WeeklyNextDueDateCalculator calculator = new WeeklyNextDueDateCalculator();

        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, Month.MARCH, 5), LocalDate.of(2026, Month.MARCH, 5));

        assertThat(next).isEqualTo(LocalDate.of(2026, Month.MARCH, 12));
    }

    @Test
    void monthlyAdvancesToTheSameDayOfMonth() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();

        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, Month.MARCH, 15), LocalDate.of(2026, Month.JANUARY, 15));

        assertThat(next).isEqualTo(LocalDate.of(2026, Month.APRIL, 15));
    }

    @Test
    void monthlyClampsAMonthEndStartDateToTheShorterMonthsLastDay() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();

        // Jan 31 -> Feb 28 (2026 is not a leap year).
        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, Month.JANUARY, 31), LocalDate.of(2026, Month.JANUARY, 31));

        assertThat(next).isEqualTo(LocalDate.of(2026, Month.FEBRUARY, 28));
    }

    @Test
    void monthlyReturnsToTheOriginalDayOnceAMonthLongEnoughComesAround() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();
        LocalDate startDate = LocalDate.of(2026, Month.JANUARY, 31);

        // Feb 28 (clamped from Jan 31) -> Mar 31, recovering the original day.
        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, Month.FEBRUARY, 28), startDate);

        assertThat(next).isEqualTo(LocalDate.of(2026, Month.MARCH, 31));
    }

    @Test
    void monthlyHandlesLeapFebruaryClamping() {
        MonthlyNextDueDateCalculator calculator = new MonthlyNextDueDateCalculator();

        // Jan 30 -> Feb (2028 is a leap year, so Feb has 29 days).
        LocalDate next = calculator.nextDueDate(LocalDate.of(2028, Month.JANUARY, 30), LocalDate.of(2028, Month.JANUARY, 30));

        assertThat(next).isEqualTo(LocalDate.of(2028, Month.FEBRUARY, 29));
    }

    @Test
    void yearlyAdvancesToTheSameMonthAndDay() {
        YearlyNextDueDateCalculator calculator = new YearlyNextDueDateCalculator();

        LocalDate next = calculator.nextDueDate(LocalDate.of(2026, Month.MAY, 20), LocalDate.of(2020, Month.MAY, 20));

        assertThat(next).isEqualTo(LocalDate.of(2027, Month.MAY, 20));
    }

    @Test
    void yearlyClampsAFeb29StartDateInANonLeapYear() {
        YearlyNextDueDateCalculator calculator = new YearlyNextDueDateCalculator();

        // Start date Feb 29 2024 (leap); advancing into 2026 (non-leap) clamps to Feb 28.
        LocalDate next = calculator.nextDueDate(LocalDate.of(2025, Month.FEBRUARY, 28), LocalDate.of(2024, Month.FEBRUARY, 29));

        assertThat(next).isEqualTo(LocalDate.of(2026, Month.FEBRUARY, 28));
    }
}
