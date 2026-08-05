package com.expensewise.report.dto;

import java.math.BigDecimal;

/** One EXPENSE category's total for the report period, with its share of totalExpense. */
public record CategoryBreakdownLine(
        Long categoryId,
        String categoryName,
        String categoryIcon,
        BigDecimal amount,
        BigDecimal percentage
) {
}
