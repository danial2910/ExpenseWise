package com.expensewise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * apiKey and model are bound with explicit @Value placeholders naming the
 * exact env var, not @ConfigurationProperties relaxed binding. Relaxed
 * binding for prefix "groq" independently derives "GROQ_API_KEY" as a
 * candidate env var for "groq.api-key" and checks it ahead of
 * application.yml's own ${GROQ_API_KEY_EXPENSEWISE} placeholder — so a
 * plain, unrelated GROQ_API_KEY set on the same machine for another
 * project silently wins over the real key. @Value resolves the literal
 * placeholder text only, with no such fallback. See DECISIONS.md.
 */
@Component
public record GroqProperties(
        @Value("${GROQ_API_KEY_EXPENSEWISE}") String apiKey,
        @Value("${GROQ_MODEL_EXPENSEWISE}") String model,
        @Value("${groq.base-url}") String baseUrl
) {
}
