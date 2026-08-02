package com.expensewise.user.dto;

import java.time.Instant;

public record LoginHistoryResponse(
        Instant occurredAt,
        String ipAddress,
        String status
) {
}
