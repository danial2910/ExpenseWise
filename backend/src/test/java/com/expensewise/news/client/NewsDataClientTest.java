package com.expensewise.news.client;

import com.expensewise.config.NewsDataProperties;
import com.expensewise.exception.NewsUnavailableException;
import com.expensewise.news.dto.ArticleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * No real NewsData.io call is ever made — MockRestServiceServer is bound
 * to the same RestClient.Builder NewsDataClient builds from, so these
 * tests are deterministic and need no real API key. Covers response
 * mapping to ArticleResponse and graceful handling of an upstream failure
 * (never a raw 500/stack trace — CLAUDE.md).
 */
class NewsDataClientTest {

    private NewsDataClient buildClient(MockRestServiceServer[] serverOut) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        serverOut[0] = server;
        NewsDataProperties properties = new NewsDataProperties("test-api-key", "https://newsdata.io/api/1");
        return new NewsDataClient(builder, properties);
    }

    @Test
    void fetchLatestNewsMapsEveryFieldFromTheNewsDataResponse() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        NewsDataClient client = buildClient(serverOut);

        String body = """
                {
                  "status": "success",
                  "totalResults": 1,
                  "results": [
                    {
                      "title": "Ringgit strengthens against US dollar",
                      "description": "The ringgit gained ground this week.",
                      "link": "https://example.com/article",
                      "pubDate": "2026-08-03 10:00:00",
                      "image_url": "https://example.com/image.jpg",
                      "source_id": "bernama",
                      "source_name": "Bernama"
                    }
                  ]
                }
                """;

        serverOut[0].expect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(requestTo(startsWith("https://newsdata.io/api/1/latest")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<ArticleResponse> articles = client.fetchLatestNews();

        assertThat(articles).hasSize(1);
        ArticleResponse article = articles.get(0);
        assertThat(article.title()).isEqualTo("Ringgit strengthens against US dollar");
        assertThat(article.snippet()).isEqualTo("The ringgit gained ground this week.");
        assertThat(article.source()).isEqualTo("Bernama");
        assertThat(article.url()).isEqualTo("https://example.com/article");
        assertThat(article.imageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(article.publishedAt()).isEqualTo(Instant.parse("2026-08-03T10:00:00Z"));
    }

    @Test
    void fetchLatestNewsFallsBackToSourceIdWhenSourceNameIsMissing() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        NewsDataClient client = buildClient(serverOut);

        String body = """
                {
                  "status": "success",
                  "results": [
                    {
                      "title": "A headline",
                      "description": null,
                      "link": "https://example.com/a",
                      "pubDate": "2026-08-01 08:00:00",
                      "image_url": null,
                      "source_id": "the_star",
                      "source_name": null
                    }
                  ]
                }
                """;

        serverOut[0].expect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(requestTo(startsWith("https://newsdata.io/api/1/latest")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<ArticleResponse> articles = client.fetchLatestNews();

        assertThat(articles.get(0).source()).isEqualTo("the_star");
        assertThat(articles.get(0).snippet()).isNull();
        assertThat(articles.get(0).imageUrl()).isNull();
    }

    @Test
    void fetchLatestNewsThrowsNewsUnavailableOnAnUpstreamServerError() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        NewsDataClient client = buildClient(serverOut);

        serverOut[0].expect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(requestTo(startsWith("https://newsdata.io/api/1/latest")))
                .andRespond(withServerError());

        assertThatThrownBy(client::fetchLatestNews)
                .isInstanceOf(NewsUnavailableException.class);
    }

    @Test
    void newsDataPropertiesRecordHoldsApiKeyAndBaseUrlAsGiven() {
        NewsDataProperties properties = new NewsDataProperties("k", "https://newsdata.io/api/1");
        assertThat(properties.apiKey()).isEqualTo("k");
        assertThat(properties.baseUrl()).isEqualTo("https://newsdata.io/api/1");
    }
}
