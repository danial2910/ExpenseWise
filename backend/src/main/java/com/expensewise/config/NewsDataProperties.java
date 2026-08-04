package com.expensewise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * apiKey is bound with an explicit @Value placeholder naming the exact env
 * var, not @ConfigurationProperties relaxed binding — same env-var
 * collision reasoning as GroqProperties (see DECISIONS.md). The constructor
 * only holds these values; NewsDataClient builds its RestClient from them
 * with no network call at startup, so the Spring context boots fine with a
 * dummy key (CI, tests).
 */
@Component
public record NewsDataProperties(
        @Value("${NEWSDATA_API_KEY_EXPENSEWISE}") String apiKey,
        @Value("${newsdata.base-url}") String baseUrl
) {
}
