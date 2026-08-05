package com.expensewise.dashboard.dto;

import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.transaction.dto.TransactionResponse;

import java.util.List;

/**
 * The single payload the Dashboard screen renders from. budgetUtilisation
 * reuses BudgetService's own BudgetMonthResponse and recentTransactions
 * reuses TransactionService's own TransactionResponse — this module
 * deliberately never recomputes budget progress or re-shapes a transaction
 * row, it only aggregates.
 */
public record DashboardResponse(
        DashboardSummaryResponse summary,
        List<MonthlyFlowPoint> monthlyTrend,
        List<CategoryAmountResponse> expenseByCategory,
        BudgetMonthResponse budgetUtilisation,
        List<TransactionResponse> recentTransactions
) {
}
