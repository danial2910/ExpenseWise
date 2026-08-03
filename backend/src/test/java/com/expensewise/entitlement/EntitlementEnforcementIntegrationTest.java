package com.expensewise.entitlement;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.entitlement.entity.UserFeatureEntitlement;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository;
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
 * Covers the two RBAC/entitlement rules called out explicitly for this
 * phase: a USER with a specific feature disabled is 403'd only on that
 * feature's endpoints, and an ADMIN is 403'd on every personal-finance
 * endpoint regardless of entitlements (admins aren't governed by the
 * entitlement table at all — see FeatureEntitlementInterceptor).
 */
class EntitlementEnforcementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserFeatureEntitlementRepository entitlementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String registerAndGetToken(String label) {
        String email = label + "+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> response = register(label, email, "Passw0rd1");
        return response.getBody().accessToken();
    }

    private void disableFeature(String token, Feature feature) {
        Long userId = restTemplate.exchange(baseUrl("/api/v1/users/me"), HttpMethod.GET,
                        new HttpEntity<>(bearerHeaders(token)), com.expensewise.user.dto.UserResponse.class)
                .getBody().id();
        UserFeatureEntitlement entitlement = entitlementRepository.findByUserIdAndFeature(userId, feature)
                .orElseThrow();
        entitlement.setEnabled(false);
        entitlementRepository.save(entitlement);
    }

    @Test
    void aUserWithBudgetsDisabledGets403OnBudgetsButNotOnTransactions() {
        String token = registerAndGetToken("budgetgated");
        disableFeature(token, Feature.BUDGETS);

        ResponseEntity<ApiErrorResponse> budgetsResponse = restTemplate.exchange(
                baseUrl("/api/v1/budgets"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)),
                ApiErrorResponse.class);
        assertThat(budgetsResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(budgetsResponse.getBody().error()).isEqualTo("FEATURE_DISABLED");

        ResponseEntity<String> transactionsResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)),
                String.class);
        assertThat(transactionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anAdminGets403OnTransactionEndpointsRegardlessOfEntitlements() {
        User admin = new User();
        admin.setEmail("adminfinance+" + System.nanoTime() + "@example.com");
        admin.setFullName("Admin Finance");
        admin.setPasswordHash(passwordEncoder.encode("Passw0rd1"));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String adminToken = login(admin.getEmail(), "Passw0rd1").getBody().accessToken();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/transactions"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
