package com.expensewise.news.dto;

import java.time.Instant;

/**
 * A single curated news article, mapped from NewsData.io's response by
 * {@code NewsDataClient}. {@code snippet}/{@code imageUrl} are nullable —
 * NewsData does not guarantee either on every article.
 */
public record ArticleResponse(
        String title,
        String snippet,
        String source,
        String url,
        String imageUrl,
        Instant publishedAt
) {
}
