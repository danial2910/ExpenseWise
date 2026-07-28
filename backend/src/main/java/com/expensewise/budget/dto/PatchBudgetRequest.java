package com.expensewise.budget.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * All fields are optional — null means "leave unchanged", same convention
 * as PatchCategoryRequest/PatchTransactionRequest. There is no separate
 * "clear the category" signal here (categoryId null means "unchanged", not
 * "make this the overall budget") — changing overall vs. category on an
 * existing budget isn't a supported edit; delete and recreate instead.
 */
public record PatchBudgetRequest(
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        Long categoryId,

        LocalDate periodMonth
) {
}
