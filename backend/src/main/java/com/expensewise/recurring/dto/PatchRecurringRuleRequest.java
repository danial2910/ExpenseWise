package com.expensewise.recurring.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * All fields are optional — null means "leave unchanged", same convention
 * as PatchBudgetRequest/PatchTransactionRequest. isActive is the pause/
 * resume affordance; every other field doubles as a general partial edit.
 */
public record PatchRecurringRuleRequest(
        @Pattern(regexp = "INCOME|EXPENSE", message = "Type must be INCOME or EXPENSE")
        String type,

        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        Long categoryId,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @Pattern(regexp = "WEEKLY|MONTHLY|YEARLY", message = "Frequency must be WEEKLY, MONTHLY or YEARLY")
        String frequency,

        LocalDate startDate,

        LocalDate endDate,

        Boolean isActive
) {
}
