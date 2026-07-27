package com.expensewise.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * All fields are optional — null means "leave unchanged", same convention
 * as PatchCategoryRequest.
 */
public record PatchTransactionRequest(
        @Pattern(regexp = "INCOME|EXPENSE", message = "Type must be INCOME or EXPENSE")
        String type,

        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        Long categoryId,

        LocalDate transactionDate,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description
) {
}
