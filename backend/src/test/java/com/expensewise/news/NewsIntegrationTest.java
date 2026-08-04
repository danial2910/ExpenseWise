package com.expensewise.news;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.entity.UserFeatureEntitlement;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository;
import com.expensewise.news.client.NewsClient;
import com.expensewise.news.dto.ArticleResponse;
import com.expensewise.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The NewsClient is entirely mocked (@MockBean) — no real NewsData.io call
 * is ever made here, matching the AI Assistant module's precedent so CI
 * needs no real API key. Covers auth (401), the NEWS entitlement gate
 * (403 when disabled), and the happy path (200 with the mocked articles).
 */
class NewsIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private NewsClient newsClient;

    @Autowired
    private UserFeatureEntitlementRepository entitlementRepository;

    private record Registered(String token, Long userId) {
    }

    private Registered registerAndGetToken(String label) {
        String email = label + "+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> response = register(label, email, "Passw0rd1");
        return new Registered(response.getBody().accessToken(), response.getBody().user().id());
    }

    private void disableNews(String token) {
        Long userId = restTemplate.exchange(baseUrl("/api/v1/users/me"), HttpMethod.GET,
                        new HttpEntity<>(bearerHeaders(token)), UserResponse.class)
                .getBody().id();
        UserFeatureEntitlement entitlement = entitlementRepository.findByUserIdAndFeature(userId, Feature.NEWS)
                .orElseThrow();
        entitlement.setEnabled(false);
        entitlementRepository.save(entitlement);
    }

    @Test
    void anUnauthenticatedRequestIsRejectedWith401() {
        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/news"), HttpMethod.GET, new HttpEntity<>(new org.springframework.http.HttpHeaders()),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aUserWithNewsDisabledGets403() {
        Mockito.when(newsClient.fetchLatestNews()).thenReturn(List.of(sampleArticle()));
        Registered user = registerAndGetToken("newsgated");
        disableNews(user.token());

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/news"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("FEATURE_DISABLED");
    }

    @Test
    void aUserWithNewsEnabledGetsTheMockedArticles() {
        Mockito.when(newsClient.fetchLatestNews()).thenReturn(List.of(sampleArticle()));
        Registered user = registerAndGetToken("newsreader");

        ResponseEntity<List<ArticleResponse>> response = restTemplate.exchange(
                baseUrl("/api/v1/news"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(user.token())),
                new ParameterizedTypeReference<List<ArticleResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).title()).isEqualTo("Test headline");
    }

    private ArticleResponse sampleArticle() {
        return new ArticleResponse("Test headline", "A snippet", "Test Source",
                "https://example.com/article", "https://example.com/image.jpg",
                Instant.parse("2026-08-01T00:00:00Z"));
    }
}
