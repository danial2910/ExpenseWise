package com.expensewise.auth;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.ForgotPasswordRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.auth.dto.ResetPasswordRequest;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.support.CapturingMailService;
import com.expensewise.user.dto.ChangePasswordRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers security requirements #3 (identical forgot-password response),
 * #4 (reset tokens hashed/single-use/expiring/invalidated on reissue),
 * #5's refresh-revocation side effect, and #10 (password change revokes
 * refresh tokens).
 */
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CapturingMailService capturingMailService;

    @Test
    void forgotPasswordReturnsTheSameResponseForKnownAndUnknownEmails() {
        String knownEmail = "known+" + System.nanoTime() + "@example.com";
        register("Known User", knownEmail, "Passw0rd1");

        ResponseEntity<Void> knownResponse = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/forgot-password"), new ForgotPasswordRequest(knownEmail), Void.class);
        ResponseEntity<Void> unknownResponse = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/forgot-password"),
                new ForgotPasswordRequest("nobody+" + System.nanoTime() + "@example.com"), Void.class);

        assertThat(knownResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknownResponse.getStatusCode()).isEqualTo(knownResponse.getStatusCode());
    }

    @Test
    void aResetTokenCanOnlyBeUsedOnceAndLogsBackInWithTheNewPassword() {
        String email = "reset+" + System.nanoTime() + "@example.com";
        register("Reset Me", email, "Passw0rd1");

        restTemplate.postForEntity(baseUrl("/api/v1/auth/forgot-password"),
                new ForgotPasswordRequest(email), Void.class);
        String token = capturingMailService.extractToken(email);

        ResponseEntity<Void> firstUse = restTemplate.postForEntity(baseUrl("/api/v1/auth/reset-password"),
                new ResetPasswordRequest(token, "NewPassw0rd1"), Void.class);
        assertThat(firstUse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiErrorResponse> secondUse = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/reset-password"),
                new ResetPasswordRequest(token, "AnotherPassw0rd1"), ApiErrorResponse.class);
        assertThat(secondUse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondUse.getBody().error()).isEqualTo("INVALID_TOKEN");

        ResponseEntity<LoginResponse> loginWithNewPassword = login(email, "NewPassw0rd1");
        assertThat(loginWithNewPassword.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void requestingANewResetTokenInvalidatesThePreviousOne() {
        String email = "reissue+" + System.nanoTime() + "@example.com";
        register("Reissue Me", email, "Passw0rd1");

        restTemplate.postForEntity(baseUrl("/api/v1/auth/forgot-password"),
                new ForgotPasswordRequest(email), Void.class);
        String firstToken = capturingMailService.extractToken(email);

        restTemplate.postForEntity(baseUrl("/api/v1/auth/forgot-password"),
                new ForgotPasswordRequest(email), Void.class);

        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/reset-password"),
                new ResetPasswordRequest(firstToken, "NewPassw0rd1"), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void resettingThePasswordRevokesExistingRefreshTokens() {
        String email = "resetrevoke+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> registerResponse = register("Reset Revoke", email, "Passw0rd1");
        String refreshCookie = extractRefreshCookie(registerResponse);

        restTemplate.postForEntity(baseUrl("/api/v1/auth/forgot-password"),
                new ForgotPasswordRequest(email), Void.class);
        String token = capturingMailService.extractToken(email);
        restTemplate.postForEntity(baseUrl("/api/v1/auth/reset-password"),
                new ResetPasswordRequest(token, "NewPassw0rd1"), Void.class);

        ResponseEntity<ApiErrorResponse> refreshAttempt = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(refreshCookie), ApiErrorResponse.class);

        assertThat(refreshAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refreshAttempt.getBody().error()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void changingThePasswordViaTheProfileEndpointRevokesExistingRefreshTokens() {
        String email = "changepw+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> registerResponse = register("Change PW", email, "Passw0rd1");
        String accessToken = registerResponse.getBody().accessToken();
        String refreshCookie = extractRefreshCookie(registerResponse);

        HttpEntity<ChangePasswordRequest> request = new HttpEntity<>(
                new ChangePasswordRequest("Passw0rd1", "BrandNewPassw0rd1"), bearerHeaders(accessToken));
        ResponseEntity<Void> changeResponse = restTemplate.exchange(
                baseUrl("/api/v1/users/me/password"), HttpMethod.PATCH, request, Void.class);
        assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiErrorResponse> refreshAttempt = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(refreshCookie), ApiErrorResponse.class);

        assertThat(refreshAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
