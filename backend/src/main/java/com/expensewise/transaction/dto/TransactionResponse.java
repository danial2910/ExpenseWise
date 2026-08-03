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
        Instant updatedAt,
        // A freshly generated signed URL when a receipt exists, regenerated
        // on every read (signed URLs expire) — never null when a receipt is
        // attached and never the same value twice. See ReceiptService.
        String receiptUrl
) {
}
