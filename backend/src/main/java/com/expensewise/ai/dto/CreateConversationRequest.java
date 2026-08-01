package com.expensewise.ai.dto;

import jakarta.validation.constraints.Size;

/**
 * firstMessage is optional — a conversation can be created empty (e.g. from
 * a "New chat" button) and get its first message posted separately via
 * POST .../messages. When present, it both becomes the first user message
 * and seeds the conversation's title (truncated).
 */
public record CreateConversationRequest(
        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String firstMessage
) {
}
