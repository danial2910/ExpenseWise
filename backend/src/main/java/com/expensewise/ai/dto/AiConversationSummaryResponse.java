package com.expensewise.ai.dto;

import java.time.Instant;

/**
 * The list-row shape for GET /api/v1/ai/conversations — no messages, so
 * the conversation history rail can render cheaply.
 */
public record AiConversationSummaryResponse(
        Long id,
        String title,
        Instant createdAt
) {
}
