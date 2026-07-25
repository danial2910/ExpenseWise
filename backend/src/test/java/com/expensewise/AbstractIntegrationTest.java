package com.expensewise;

import com.expensewise.auth.dto.LoginRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.auth.dto.RegisterRequest;
import jakarta.annotation.PostConstruct;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * Base class for full-stack integration tests. Runs against the project's
 * own docker-compose Postgres on localhost:5433 (never Supabase) — the
 * same database CLAUDE.md designates for local dev and e2e testing.
 *
 * <p>The original plan used a Testcontainers-managed throwaway Postgres
 * for full isolation, but Testcontainers' npipe client hits a Docker
 * Desktop-on-Windows compatibility bug in this dev environment (the CLI
 * talks to the daemon fine; docker-java's raw npipe requests get a
 * malformed response). Testcontainers works fine on Linux CI runners, so
 * this fallback is a local-dev-only adaptation — see DECISIONS.md. Tests
 * use uniquely generated emails so they don't collide with real dev data
 * in the same database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * The default client backing TestRestTemplate auto-captures Set-Cookie
     * responses and silently re-sends the latest one, overriding whatever
     * Cookie header a test sets manually. Refresh-token tests need to
     * control exactly which cookie is sent, so cookie management is
     * disabled here.
     */
    @PostConstruct
    void disableAutomaticCookieHandling() {
        var factory = new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableCookieManagement().build());
        restTemplate.getRestTemplate().setRequestFactory(factory);
    }

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    protected ResponseEntity<LoginResponse> register(String fullName, String email, String password) {
        return restTemplate.postForEntity(baseUrl("/api/v1/auth/register"),
                new RegisterRequest(fullName, email, password), LoginResponse.class);
    }

    protected ResponseEntity<LoginResponse> login(String email, String password) {
        return restTemplate.postForEntity(baseUrl("/api/v1/auth/login"),
                new LoginRequest(email, password), LoginResponse.class);
    }

    protected HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    protected String extractRefreshCookie(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return null;
        }
        return setCookies.stream()
                .filter(cookie -> cookie.startsWith("refreshToken="))
                .findFirst()
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .orElse(null);
    }

    protected HttpEntity<Void> withCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new HttpEntity<>(headers);
    }
}
