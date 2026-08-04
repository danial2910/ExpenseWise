package com.expensewise.news.service;

import com.expensewise.config.CacheConfig;
import com.expensewise.news.client.NewsClient;
import com.expensewise.news.dto.ArticleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the real {@code @Cacheable} proxy (CacheConfig's
 * CaffeineCacheManager) — a plain Mockito test can't catch a caching bug
 * since {@code @Cacheable} only applies through a Spring-generated proxy.
 * {@code newsdata.cache-ttl-seconds} is shortened here so the expiry case
 * doesn't need a long sleep; NewsClient is mocked, so no real NewsData.io
 * call is ever made.
 *
 * <p>Uses a full web context (not WebEnvironment.NONE) because
 * SecurityConfig's MvcRequestMatcher-based rules need the MVC
 * infrastructure (mvcHandlerMappingIntrospector) present in the same
 * context — same reasoning AbstractIntegrationTest already relies on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@TestPropertySource(properties = "newsdata.cache-ttl-seconds=1")
class NewsServiceCacheTest {

    @Autowired
    private NewsService newsService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private NewsClient newsClient;

    // The cache is a shared Spring-managed bean that otherwise outlives a
    // single test method within this class's cached ApplicationContext —
    // without clearing it, a later test can see an earlier test's cached
    // entry and look like it never called the (freshly re-stubbed) mock.
    @BeforeEach
    void clearNewsCache() {
        Cache cache = cacheManager.getCache(CacheConfig.NEWS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void aSecondCallWithinTheTtlIsServedFromCacheWithoutRecallingTheClient() {
        List<ArticleResponse> articles = List.of(sampleArticle("First headline"));
        when(newsClient.fetchLatestNews()).thenReturn(articles);

        List<ArticleResponse> first = newsService.getLatestNews();
        List<ArticleResponse> second = newsService.getLatestNews();

        assertThat(first).isEqualTo(articles);
        assertThat(second).isEqualTo(articles);
        verify(newsClient, times(1)).fetchLatestNews();
    }

    @Test
    void aCallAfterTheTtlExpiresRefetchesFromTheClient() throws InterruptedException {
        when(newsClient.fetchLatestNews())
                .thenReturn(List.of(sampleArticle("Before expiry")))
                .thenReturn(List.of(sampleArticle("After expiry")));

        List<ArticleResponse> first = newsService.getLatestNews();
        assertThat(first.get(0).title()).isEqualTo("Before expiry");

        // newsdata.cache-ttl-seconds=1 for this test class.
        Thread.sleep(1500);

        List<ArticleResponse> second = newsService.getLatestNews();
        assertThat(second.get(0).title()).isEqualTo("After expiry");
        verify(newsClient, times(2)).fetchLatestNews();
    }

    private ArticleResponse sampleArticle(String title) {
        return new ArticleResponse(title, "snippet", "source", "https://example.com", null, null);
    }
}
