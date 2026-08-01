package com.expensewise.ai.client;

/**
 * A provider-agnostic chat message (role is "system", "user", or
 * "assistant") — the seam AiService talks through, so tests can mock
 * AiChatClient without knowing anything about Groq's wire format.
 */
public record AiChatMessage(String role, String content) {
}
