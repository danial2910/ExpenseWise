package com.expensewise.news.service;

import com.expensewise.config.CacheConfig;
import com.expensewise.news.client.NewsClient;
import com.expensewise.news.dto.ArticleResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The cached result is shared across every user, not per-user — a single
 * {@code @Cacheable} entry with no key, so one upstream NewsData.io call
 * serves everyone within the TTL window (CacheConfig). See DECISIONS.md.
 */
@Service
public class NewsService {

    private final NewsClient newsClient;

    public NewsService(NewsClient newsClient) {
        this.newsClient = newsClient;
    }

    @Cacheable(CacheConfig.NEWS_CACHE)
    public List<ArticleResponse> getLatestNews() {
        return newsClient.fetchLatestNews();
    }
}
