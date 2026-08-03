package com.expensewise.recurring.dto;

/** Result of a manual "generate due now" trigger — CLAUDE.md's demo endpoint. */
public record GenerateDueResponse(int transactionsGenerated) {
}
