package com.expensewise.admin;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.admin.dto.AdminCreateUserRequest;
import com.expensewise.admin.dto.AdminUpdateUserAccessRequest;
import com.expensewise.admin.dto.AdminUserDetailResponse;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.entitlement.Feature;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private record AdminSession(Long id, String token) {
    }

    private AdminSession seedAdmin() {
        User admin = new User();
        admin.setEmail("admin+" + System.nanoTime() + "@example.com");
        admin.setFullName("Admin User");
        admin.setPasswordHash(passwordEncoder.encode("Passw0rd1"));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String token = login(admin.getEmail(), "Passw0rd1").getBody().accessToken();
        return new AdminSession(admin.getId(), token);
    }

    @Test
    void listingUsersWithNoFiltersDoesNotBlowUpOnTheNullSearchParameter() {
        AdminSession admin = seedAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl("/api/v1/admin/users"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(admin.token())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void creatingAUserSeedsExactlyTheRequestedFeaturesAndNoPassword() {
        AdminSession admin = seedAdmin();
        String email = "created+" + System.nanoTime() + "@example.com";

        ResponseEntity<AdminUserDetailResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/admin/users"), HttpMethod.POST,
                new HttpEntity<>(new AdminCreateUserRequest("Created User", email, "USER", Set.of(Feature.TRANSACTIONS)),
                        bearerHeaders(admin.token())),
                AdminUserDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().enabledFeatures().get(Feature.TRANSACTIONS)).isTrue();
        assertThat(response.getBody().enabledFeatures().get(Feature.BUDGETS)).isFalse();

        // No password was ever handed out — the account can't log in until it sets one.
        ResponseEntity<ApiErrorResponse> loginAttempt = restTemplate.exchange(
                baseUrl("/api/v1/auth/login"), HttpMethod.POST,
                new HttpEntity<>(new com.expensewise.auth.dto.LoginRequest(email, "anything")),
                ApiErrorResponse.class);
        assertThat(loginAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updatingAccessAtomicallyChangesRoleStatusAndFeatures() {
        AdminSession admin = seedAdmin();
        ResponseEntity<LoginResponse> targetRegistration = register("Target User",
                "target+" + System.nanoTime() + "@example.com", "Passw0rd1");
        Long targetId = targetRegistration.getBody().user().id();

        ResponseEntity<AdminUserDetailResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + targetId + "/access"), HttpMethod.PUT,
                new HttpEntity<>(new AdminUpdateUserAccessRequest("USER", false, Set.of(Feature.AI_ASSISTANT)),
                        bearerHeaders(admin.token())),
                AdminUserDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().user().active()).isFalse();
        assertThat(response.getBody().enabledFeatures().get(Feature.AI_ASSISTANT)).isTrue();
        assertThat(response.getBody().enabledFeatures().get(Feature.BUDGETS)).isFalse();
    }

    @Test
    void anAdminCannotHardDeleteTheirOwnAccount() {
        AdminSession admin = seedAdmin();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + admin.id()), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(admin.token())), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void hardDeletingAnotherUserRemovesTheirAccountEntirely() {
        AdminSession admin = seedAdmin();
        ResponseEntity<LoginResponse> targetRegistration = register("Delete Me",
                "deleteme+" + System.nanoTime() + "@example.com", "Passw0rd1");
        Long targetId = targetRegistration.getBody().user().id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + targetId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(admin.token())), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(userRepository.findById(targetId)).isEmpty();
    }

    @Test
    void nonAdminGets403OnCreateAndDeleteEndpoints() {
        String token = register("Regular", "regular+" + System.nanoTime() + "@example.com", "Passw0rd1")
                .getBody().accessToken();

        ResponseEntity<ApiErrorResponse> createResponse = restTemplate.exchange(
                baseUrl("/api/v1/admin/users"), HttpMethod.POST,
                new HttpEntity<>(new AdminCreateUserRequest("X", "x+" + System.nanoTime() + "@example.com", "USER", null),
                        bearerHeaders(token)),
                ApiErrorResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/1"), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
