package com.expensewise.dashboard.dto;

import java.math.BigDecimal;

/** One category's total EXPENSE amount for the current month, for the "Spending by Category" chart. */
public record CategoryAmountResponse(
        Long categoryId,
        String categoryName,
        String categoryIcon,
        BigDecimal amount
) {
}
