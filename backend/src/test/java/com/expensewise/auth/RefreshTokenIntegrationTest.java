package com.expensewise.auth;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.auth.dto.RefreshResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.common.repository.ActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers security requirements #5 (rotation + reuse revokes the whole
 * family), #6 (login rate limiting), and the activity-log requirement
 * (#7) for a couple of representative events.
 */
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Test
    void refreshRotatesTheTokenAndTheOldCookieCanNoLongerBeUsed() {
        String email = "rotate+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> registerResponse = register("Rotate Me", email, "Passw0rd1");
        String firstCookie = extractRefreshCookie(registerResponse);

        ResponseEntity<RefreshResponse> refreshResponse = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(firstCookie), RefreshResponse.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();

        ResponseEntity<ApiErrorResponse> reuseAttempt = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(firstCookie), ApiErrorResponse.class);
        assertThat(reuseAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reusingARevokedRefreshTokenRevokesEveryOtherActiveTokenForThatUser() {
        String email = "family+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> registerResponse = register("Family Test", email, "Passw0rd1");
        String cookieA = extractRefreshCookie(registerResponse);

        ResponseEntity<RefreshResponse> rotateResponse = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(cookieA), RefreshResponse.class);
        String cookieB = extractRefreshCookie(rotateResponse);
        assertThat(cookieB).isNotNull();

        // Reusing the now-revoked cookieA is treated as a compromise signal.
        restTemplate.exchange(baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(cookieA),
                ApiErrorResponse.class);

        // cookieB descended from the same login and must be revoked too.
        ResponseEntity<ApiErrorResponse> cookieBAttempt = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(cookieB), ApiErrorResponse.class);
        assertThat(cookieBAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() {
        String email = "logout+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> registerResponse = register("Logout Test", email, "Passw0rd1");
        String cookie = extractRefreshCookie(registerResponse);

        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                baseUrl("/api/v1/auth/logout"), HttpMethod.POST, withCookie(cookie), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiErrorResponse> refreshAttempt = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(cookie), ApiErrorResponse.class);
        assertThat(refreshAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        boolean hasLogoutEntry = activityLogRepository.findAll().stream()
                .anyMatch(log -> "LOGOUT".equals(log.getAction())
                        && log.getUserId() != null
                        && log.getUserId().equals(registerResponse.getBody().user().id()));
        assertThat(hasLogoutEntry).isTrue();
    }

    @Test
    void loginIsRateLimitedAfterFiveFailedAttemptsAndSucceedsAreLogged() {
        String email = "ratelimited+" + System.nanoTime() + "@example.com";
        register("Rate Limited", email, "Passw0rd1");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<ApiErrorResponse> attempt = restTemplate.postForEntity(
                    baseUrl("/api/v1/auth/login"), new LoginRequest(email, "WrongPassword" + i),
                    ApiErrorResponse.class);
            assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<ApiErrorResponse> sixthAttempt = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd1"), ApiErrorResponse.class);

        assertThat(sixthAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixthAttempt.getBody().error()).isEqualTo("RATE_LIMITED");

        long failedLogEntries = activityLogRepository.findAll().stream()
                .filter(log -> "LOGIN_FAILED".equals(log.getAction()))
                .count();
        assertThat(failedLogEntries).isGreaterThanOrEqualTo(5);
    }
}
