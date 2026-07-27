package com.expensewise.transaction.dto;

import java.math.BigDecimal;

public record TransactionSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance
) {
}
