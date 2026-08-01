package com.expensewise.ai.client;

import java.util.List;

/**
 * The seam between AiService and whichever model backend answers a chat —
 * currently Groq (GroqChatClient). Tests replace this with a mock so no
 * real network call is made and replies are deterministic. See
 * DECISIONS.md for why this is a plain interface + RestClient call rather
 * than a Spring AI ChatModel.
 */
public interface AiChatClient {

    /**
     * Sends the full message list (system prompt first, then the
     * conversation history in order) and returns the assistant's reply
     * text, waiting for the complete (non-streaming) response.
     */
    String chat(List<AiChatMessage> messages);
}
