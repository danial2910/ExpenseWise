package com.expensewise.ai.dto;

import java.time.Instant;
import java.util.List;

public record AiConversationResponse(
        Long id,
        String title,
        Instant createdAt,
        List<AiMessageResponse> messages
) {
}
