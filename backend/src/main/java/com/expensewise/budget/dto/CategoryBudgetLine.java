package com.expensewise.budget.dto;

import java.math.BigDecimal;

/**
 * One row per EXPENSE category visible to the caller inside
 * BudgetMonthResponse — every category appears even when it has no budget
 * set yet (budgetId/amount/remaining/progressPercent null), so the screen
 * can offer "Set budget" per the design.
 */
public record CategoryBudgetLine(
        Long categoryId,
        String categoryName,
        String categoryIcon,
        Long budgetId,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal progressPercent,
        boolean exceeded
) {
}
