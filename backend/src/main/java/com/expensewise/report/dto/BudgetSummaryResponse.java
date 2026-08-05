package com.expensewise.report.dto;

import java.math.BigDecimal;

/**
 * Reuses BudgetService's own overall-budget figures — never recomputed here.
 * For a MONTHLY report this is that month's overall budget line; for a
 * YEARLY report it's the sum of every month's overall budget (only months
 * that actually had one set contribute to totalBudgeted). totalBudgeted and
 * totalRemaining are null when hasBudget is false — no limit was ever set
 * for the period, so there's nothing to compare spending against.
 */
public record BudgetSummaryResponse(
        BigDecimal totalBudgeted,
        BigDecimal totalSpent,
        BigDecimal totalRemaining,
        boolean hasBudget
) {
}
