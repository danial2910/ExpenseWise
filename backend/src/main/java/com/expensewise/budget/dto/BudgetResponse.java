package com.expensewise.budget.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single budget row (overall when categoryId is null) with spent /
 * remaining / progressPercent / exceeded computed live at request time —
 * never stored. Returned by the single-budget CRUD endpoints.
 */
public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        LocalDate periodMonth,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal progressPercent,
        boolean exceeded,
        Instant createdAt
) {
}
