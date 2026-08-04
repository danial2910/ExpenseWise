package com.expensewise.news.client;

import com.expensewise.news.dto.ArticleResponse;

import java.util.List;

/**
 * The seam between NewsService and whichever news backend supplies
 * articles — currently NewsData.io (NewsDataClient). Tests replace this
 * with a mock so no real network call is made and results are
 * deterministic. Mirrors the AiChatClient/GroqChatClient seam — see
 * DECISIONS.md.
 */
public interface NewsClient {

    /**
     * Fetches the current batch of curated financial/personal-finance
     * articles, English only. Throws {@code NewsUnavailableException} if
     * the upstream call fails or returns something unusable.
     */
    List<ArticleResponse> fetchLatestNews();
}
