package com.expensewise.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Used for both create (POST) and full update (PUT) — same convention as
 * TransactionRequest/CategoryRequest. categoryId is intentionally not
 * @NotNull: null means the overall monthly budget.
 */
public record BudgetRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        Long categoryId,

        @NotNull(message = "Period month is required")
        LocalDate periodMonth
) {
}
