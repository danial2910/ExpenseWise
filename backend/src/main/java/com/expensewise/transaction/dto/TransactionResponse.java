package com.expensewise.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        Long categoryId,
        String categoryName,
        String categoryIcon,
        LocalDate transactionDate,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
