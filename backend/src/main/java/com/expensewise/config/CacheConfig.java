package com.expensewise.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Backs the News module's shared cache (one upstream NewsData.io call
 * serves every user within the TTL window) — see DECISIONS.md for why
 * Caffeine over a hand-rolled cache. {@code newsdata.cache-ttl-seconds}
 * defaults to 1200 (20 min) in application.yml; tests override it to a
 * short value to exercise expiry without a long sleep.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String NEWS_CACHE = "news";

    @Value("${newsdata.cache-ttl-seconds}")
    private long newsCacheTtlSeconds;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(NEWS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(newsCacheTtlSeconds)));
        return manager;
    }
}
