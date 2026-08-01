package com.expensewise.ai.dto;

import java.time.Instant;

public record AiMessageResponse(
        Long id,
        String role,
        String content,
        Instant createdAt
) {
}
