package com.expensewise.budget.dto;

import java.math.BigDecimal;

/**
 * The overall monthly budget row inside BudgetMonthResponse. budgetId and
 * amount are null when the user hasn't set an overall budget for the month
 * — spent is still computed so the screen can show "RM X spent, no limit
 * set" per the design's empty state.
 */
public record OverallBudgetLine(
        Long budgetId,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal progressPercent,
        boolean exceeded
) {
}
