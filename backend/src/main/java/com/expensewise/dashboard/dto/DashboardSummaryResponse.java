package com.expensewise.dashboard.dto;

import java.math.BigDecimal;

/**
 * thisMonth* figures are scoped to the current Asia/Kuala_Lumpur calendar
 * month; overallBalance is all-time (every transaction ever recorded).
 */
public record DashboardSummaryResponse(
        BigDecimal thisMonthIncome,
        BigDecimal thisMonthExpense,
        BigDecimal thisMonthBalance,
        BigDecimal overallBalance
) {
}
