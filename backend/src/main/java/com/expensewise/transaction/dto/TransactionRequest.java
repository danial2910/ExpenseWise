package com.expensewise.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Used for both create (POST) and full update (PUT) — the required fields
 * are identical for each, same convention as CategoryRequest.
 */
public record TransactionRequest(
        @NotBlank(message = "Type is required")
        @Pattern(regexp = "INCOME|EXPENSE", message = "Type must be INCOME or EXPENSE")
        String type,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Category is required")
        Long categoryId,

        @NotNull(message = "Transaction date is required")
        LocalDate transactionDate,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description
) {
}
