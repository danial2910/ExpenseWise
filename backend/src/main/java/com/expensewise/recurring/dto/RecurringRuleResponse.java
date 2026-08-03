package com.expensewise.recurring.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringRuleResponse(
        Long id,
        String type,
        BigDecimal amount,
        Long categoryId,
        String categoryName,
        String categoryIcon,
        String description,
        String frequency,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextDueDate,
        boolean isActive
) {
}
