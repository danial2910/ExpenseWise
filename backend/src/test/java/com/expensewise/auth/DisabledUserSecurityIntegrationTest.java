package com.expensewise.auth;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.admin.dto.UserStatusRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers security requirements #1 (disabled user rejected even with a
 * valid access token), #2 (disabling revokes refresh tokens), #9 (admin
 * cannot disable self), and the "non-admin gets 403 on admin endpoints"
 * check called out explicitly in CLAUDE.md.
 */
class DisabledUserSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User seedAdmin(String email, String password) {
        User admin = new User();
        admin.setEmail(email);
        admin.setFullName("Admin User");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        return userRepository.save(admin);
    }

    @Test
    void disablingAUserRejectsItsAlreadyIssuedAccessTokenOnTheVeryNextRequest() {
        String email = "todisable+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd1";
        LoginResponse registered = register("To Disable", email, password).getBody();
        String accessToken = registered.accessToken();

        String adminEmail = "admin1+" + System.nanoTime() + "@example.com";
        seedAdmin(adminEmail, password);
        String adminToken = login(adminEmail, password).getBody().accessToken();

        // sanity check: the token works before being disabled
        ResponseEntity<UserResponse> before = restTemplate.exchange(
                baseUrl("/api/v1/users/me"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(accessToken)),
                UserResponse.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

        restTemplate.exchange(baseUrl("/api/v1/admin/users/" + registered.user().id() + "/status"),
                HttpMethod.PATCH, new HttpEntity<>(new UserStatusRequest(false), bearerHeaders(adminToken)),
                UserResponse.class);

        ResponseEntity<ApiErrorResponse> after = restTemplate.exchange(
                baseUrl("/api/v1/users/me"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(accessToken)),
                ApiErrorResponse.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void disablingAUserRevokesItsRefreshToken() {
        String email = "revoke+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd1";
        ResponseEntity<LoginResponse> registerResponse = register("Revoke Me", email, password);
        String refreshCookie = extractRefreshCookie(registerResponse);
        Long userId = registerResponse.getBody().user().id();

        String adminEmail = "admin2+" + System.nanoTime() + "@example.com";
        seedAdmin(adminEmail, password);
        String adminToken = login(adminEmail, password).getBody().accessToken();

        restTemplate.exchange(baseUrl("/api/v1/admin/users/" + userId + "/status"),
                HttpMethod.PATCH, new HttpEntity<>(new UserStatusRequest(false), bearerHeaders(adminToken)),
                UserResponse.class);

        ResponseEntity<ApiErrorResponse> refreshAttempt = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"), HttpMethod.POST, withCookie(refreshCookie), ApiErrorResponse.class);

        assertThat(refreshAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refreshAttempt.getBody().error()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() {
        String adminEmail = "selfdisable+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd1";
        User admin = seedAdmin(adminEmail, password);
        String adminToken = login(adminEmail, password).getBody().accessToken();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + admin.getId() + "/status"),
                HttpMethod.PATCH, new HttpEntity<>(new UserStatusRequest(false), bearerHeaders(adminToken)),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aNonAdminGets403OnAdminEndpoints() {
        String email = "regular+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd1";
        String accessToken = register("Regular User", email, password).getBody().accessToken();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/admin/users"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(accessToken)),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
