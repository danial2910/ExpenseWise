package com.expensewise.ai.dto;

/**
 * One card in the "Spending Analysis" panel. Computed deterministically
 * from the caller's own budget/transaction data (BudgetService,
 * TransactionService) — no model call involved, so severity is exact, not
 * AI-inferred. See DECISIONS.md.
 *
 * severity is one of CRITICAL (over budget), WARNING (approaching a
 * limit), or POSITIVE (a favourable outcome worth surfacing, e.g. net
 * savings this month) — the frontend maps these to red/amber/green.
 */
public record InsightResponse(
        String title,
        String body,
        String severity
) {
}
