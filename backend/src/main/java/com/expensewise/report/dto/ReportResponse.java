package com.expensewise.report.dto;

import com.expensewise.dashboard.dto.MonthlyFlowPoint;
import com.expensewise.transaction.dto.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The full report data model — the same object both the JSON preview
 * endpoint and both file exporters (Excel/PDF) are built from, so the
 * on-screen preview and the downloaded file can never drift apart.
 * transactions reuses TransactionService's own TransactionResponse;
 * monthlyTrend reuses the Dashboard module's own MonthlyFlowPoint (same
 * income/expense-per-month shape, one canonical DTO for it) rather than a
 * parallel report-only type. Entities never leave the service layer.
 * monthlyTrend is screen-only — neither exporter renders it, since a
 * PDF/Excel financial statement for one period has no use for a trailing
 * multi-month trend the way an on-screen chart does.
 */
public record ReportResponse(
        String type,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netBalance,
        List<CategoryBreakdownLine> categoryBreakdown,
        List<TransactionResponse> transactions,
        BudgetSummaryResponse budgetSummary,
        List<MonthlyFlowPoint> monthlyTrend
) {
}
