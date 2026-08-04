package com.expensewise.news.client;

import com.expensewise.config.NewsDataProperties;
import com.expensewise.exception.NewsUnavailableException;
import com.expensewise.news.dto.ArticleResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Calls NewsData.io's "latest news" endpoint directly via RestClient — no
 * SDK dependency, mirroring GroqChatClient/SupabaseStorageService's
 * "boring, explicit code" precedent (see DECISIONS.md). The query is
 * tuned to financial/personal-finance content, English only.
 */
@Component
public class NewsDataClient implements NewsClient {

    // NewsData.io returns pubDate as "yyyy-MM-dd HH:mm:ss" with no offset,
    // documented as UTC.
    private static final DateTimeFormatter PUB_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final String apiKey;

    // Takes Spring Boot's auto-configured RestClient.Builder (rather than
    // calling RestClient.builder() directly, as GroqChatClient does) so a
    // test can bind MockRestServiceServer to it and assert on the mapped
    // response without a real network call.
    public NewsDataClient(RestClient.Builder restClientBuilder, NewsDataProperties newsDataProperties) {
        this.restClient = restClientBuilder
                .baseUrl(newsDataProperties.baseUrl())
                .build();
        this.apiKey = newsDataProperties.apiKey();
    }

    @Override
    public List<ArticleResponse> fetchLatestNews() {
        try {
            NewsDataResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/latest")
                            .queryParam("apikey", apiKey)
                            .queryParam("category", "business")
                            .queryParam("language", "en")
                            .queryParam("q", "finance OR budgeting OR savings OR investing")
                            .build())
                    .retrieve()
                    .body(NewsDataResponse.class);

            if (response == null || response.results() == null) {
                throw new NewsUnavailableException("The news service returned an empty response");
            }
            return response.results().stream().map(this::toArticleResponse).toList();
        } catch (RestClientException ex) {
            throw new NewsUnavailableException("The news service is temporarily unavailable");
        }
    }

    private ArticleResponse toArticleResponse(NewsDataArticle article) {
        return new ArticleResponse(
                article.title(),
                article.description(),
                article.sourceName() != null ? article.sourceName() : article.sourceId(),
                article.link(),
                article.imageUrl(),
                parsePublishedAt(article.pubDate())
        );
    }

    private Instant parsePublishedAt(String pubDate) {
        if (pubDate == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(pubDate, PUB_DATE_FORMAT).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private record NewsDataResponse(String status, List<NewsDataArticle> results) {
    }

    private record NewsDataArticle(
            String title,
            String description,
            String link,
            String pubDate,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("source_id") String sourceId,
            @JsonProperty("source_name") String sourceName
    ) {
    }
}
