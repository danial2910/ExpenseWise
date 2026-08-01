package com.expensewise.ai.client;

import com.expensewise.config.GroqProperties;
import com.expensewise.exception.AiChatUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Calls Groq's OpenAI-compatible chat-completions endpoint directly via
 * RestClient — no Spring AI dependency. Spring AI's current releases
 * require Spring Boot 3.4.x/3.5.x (1.0.x) or 4.1.x (2.0.0), neither of
 * which matches this project's pinned Spring Boot 3.3.4, so a direct REST
 * call was chosen over bumping the whole app's Spring Boot version for one
 * module. Groq's request/response shape is the same as OpenAI's chat
 * completions API. See DECISIONS.md.
 */
@Component
public class GroqChatClient implements AiChatClient {

    private final RestClient restClient;
    private final String model;

    public GroqChatClient(GroqProperties groqProperties) {
        this.restClient = RestClient.builder()
                .baseUrl(groqProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + groqProperties.apiKey())
                .build();
        this.model = groqProperties.model();
    }

    @Override
    public String chat(List<AiChatMessage> messages) {
        List<GroqMessage> groqMessages = messages.stream()
                .map(m -> new GroqMessage(m.role(), m.content()))
                .toList();

        try {
            GroqChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(new GroqChatRequest(model, groqMessages))
                    .retrieve()
                    .body(GroqChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AiChatUnavailableException("The AI assistant returned an empty response");
            }
            return response.choices().get(0).message().content();
        } catch (RestClientException ex) {
            throw new AiChatUnavailableException("The AI assistant is temporarily unavailable");
        }
    }

    private record GroqChatRequest(String model, List<GroqMessage> messages) {
    }

    private record GroqMessage(String role, String content) {
    }

    private record GroqChatResponse(List<GroqChoice> choices) {
    }

    private record GroqChoice(GroqMessage message) {
    }
}
