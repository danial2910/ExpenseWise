package com.expensewise.auth;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.auth.dto.RegisterRequest;
import com.expensewise.common.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerThenLoginThenFetchCurrentUser() {
        String email = "sarah.lim+" + System.nanoTime() + "@example.com";

        ResponseEntity<LoginResponse> registerResponse = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"),
                new RegisterRequest("Sarah Lim", email, "Passw0rd1"),
                LoginResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().user().email()).isEqualTo(email);
        assertThat(registerResponse.getBody().accessToken()).isNotBlank();

        String accessToken = registerResponse.getBody().accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> meResponse = restTemplate.exchange(
                baseUrl("/api/v1/users/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).contains(email);
    }

    @Test
    void registeringWithAnAlreadyUsedEmailIsRejected() {
        String email = "duplicate+" + System.nanoTime() + "@example.com";
        restTemplate.postForEntity(baseUrl("/api/v1/auth/register"),
                new RegisterRequest("Sarah Lim", email, "Passw0rd1"), LoginResponse.class);

        ResponseEntity<ApiErrorResponse> second = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"),
                new RegisterRequest("Sarah Lim", email, "Passw0rd1"),
                ApiErrorResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().error()).isEqualTo("VALIDATION_FAILED");
        assertThat(second.getBody().fieldErrors()).containsKey("email");
    }

    @Test
    void registeringWithAWeakPasswordIsRejected() {
        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"),
                new RegisterRequest("Sarah Lim", "weakpw+" + System.nanoTime() + "@example.com", "weak"),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).containsKey("password");
    }

    @Test
    void loginWithWrongPasswordIsRejectedWithoutRevealingWhichFieldIsWrong() {
        String email = "wrongpw+" + System.nanoTime() + "@example.com";
        restTemplate.postForEntity(baseUrl("/api/v1/auth/register"),
                new RegisterRequest("Sarah Lim", email, "Passw0rd1"), LoginResponse.class);

        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                new LoginRequest(email, "WrongPassword1"),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void aRequestWithNoAccessTokenIsRejected() {
        ResponseEntity<ApiErrorResponse> response = restTemplate.getForEntity(
                baseUrl("/api/v1/users/me"), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
