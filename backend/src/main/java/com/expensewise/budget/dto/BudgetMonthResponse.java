package com.expensewise.budget.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The single-call payload the Budgets screen renders from: the resolved
 * month, the overall budget (if any), and every EXPENSE category with its
 * budget (if any) — all enriched with computed spent/remaining/progress.
 */
public record BudgetMonthResponse(
        LocalDate periodMonth,
        OverallBudgetLine overall,
        List<CategoryBudgetLine> categories
) {
}
