package com.expensewise.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point in the trend/comparison charts: total income and expense for a
 * calendar month (first day of the month, Asia/Kuala_Lumpur). Both the
 * income-vs-expense comparison and the net/savings trend are derived from
 * this single series on the frontend (net = income - expense) rather than
 * two separate endpoints returning the same underlying figures twice.
 */
public record MonthlyFlowPoint(
        LocalDate month,
        BigDecimal income,
        BigDecimal expense
) {
}
