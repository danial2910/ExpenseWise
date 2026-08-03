package com.expensewise.recurring.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Used for both create (POST) and full update (PUT) — same convention as
 * TransactionRequest/BudgetRequest. endDate is optional (null = no end);
 * end-after-start is validated in RecurringRuleService, not an annotation,
 * since it's a cross-field rule.
 */
public record RecurringRuleRequest(
        @NotBlank(message = "Type is required")
        @Pattern(regexp = "INCOME|EXPENSE", message = "Type must be INCOME or EXPENSE")
        String type,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Category is required")
        Long categoryId,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotBlank(message = "Frequency is required")
        @Pattern(regexp = "WEEKLY|MONTHLY|YEARLY", message = "Frequency must be WEEKLY, MONTHLY or YEARLY")
        String frequency,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate
) {
}
