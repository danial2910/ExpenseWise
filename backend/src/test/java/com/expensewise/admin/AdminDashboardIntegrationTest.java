package com.expensewise.admin;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.admin.dto.AdminDashboardResponse;
import com.expensewise.admin.dto.FeatureUsageResponse;
import com.expensewise.admin.dto.RecentSignupResponse;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.budget.dto.BudgetResponse;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.entity.UserFeatureEntitlement;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository;
import com.expensewise.transaction.dto.TransactionRequest;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev Postgres this suite runs against (see AbstractIntegrationTest) is
 * shared and cumulative across test runs, so absolute counts ("there are
 * exactly N users") would be flaky. Every assertion here instead compares a
 * baseline dashboard snapshot (captured before seeding) against a second
 * snapshot after seeding a known, uniquely-identified set of rows, and
 * checks the delta — the one thing guaranteed regardless of pre-existing
 * data from earlier test runs.
 */
class AdminDashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFeatureEntitlementRepository entitlementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private record Registered(String token, Long userId, String email) {
    }

    private User seedAdmin() {
        User admin = new User();
        admin.setEmail("dashboardadmin+" + System.nanoTime() + "@example.com");
        admin.setFullName("Dashboard Admin");
        admin.setPasswordHash(passwordEncoder.encode("Passw0rd1"));
        admin.setRole("ADMIN");
        return userRepository.save(admin);
    }

    private Registered registerAndGetToken(String label) {
        String email = label + "+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> response = register(label, email, "Passw0rd1");
        return new Registered(response.getBody().accessToken(), response.getBody().user().id(), email);
    }

    private ResponseEntity<AdminDashboardResponse> fetchDashboard(String adminToken) {
        return restTemplate.exchange(baseUrl("/api/v1/admin/dashboard"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), AdminDashboardResponse.class);
    }

    private long enabledCountFor(AdminDashboardResponse dashboard, Feature feature) {
        return dashboard.featureUsage().stream()
                .filter(f -> f.feature() == feature)
                .findFirst()
                .map(FeatureUsageResponse::enabledCount)
                .orElseThrow();
    }

    @Test
    void newlyRegisteredUsersIncreaseTotalActiveAndNewThisMonthCounts() {
        User admin = seedAdmin();
        String adminToken = login(admin.getEmail(), "Passw0rd1").getBody().accessToken();

        AdminDashboardResponse before = fetchDashboard(adminToken).getBody();

        registerAndGetToken("dashcount1");
        registerAndGetToken("dashcount2");

        AdminDashboardResponse after = fetchDashboard(adminToken).getBody();

        assertThat(after.summary().totalUsers() - before.summary().totalUsers()).isEqualTo(2);
        assertThat(after.summary().activeUsers() - before.summary().activeUsers()).isEqualTo(2);
        assertThat(after.summary().regularUsers() - before.summary().regularUsers()).isEqualTo(2);
        // Both accounts are created "now" by the real system clock, so they
        // fall in the current Asia/Kuala_Lumpur month regardless of when
        // this test happens to run.
        assertThat(after.summary().newUsersThisMonth() - before.summary().newUsersThisMonth()).isEqualTo(2);
    }

    @Test
    void disablingAFeatureForOneUserButNotAnotherIsReflectedInFeatureUsageCounts() {
        User admin = seedAdmin();
        String adminToken = login(admin.getEmail(), "Passw0rd1").getBody().accessToken();

        AdminDashboardResponse before = fetchDashboard(adminToken).getBody();
        long budgetsEnabledBefore = enabledCountFor(before, Feature.BUDGETS);
        long transactionsEnabledBefore = enabledCountFor(before, Feature.TRANSACTIONS);

        registerAndGetToken("dashfeatureon");
        Registered withoutBudgets = registerAndGetToken("dashfeatureoff");
        UserFeatureEntitlement budgetsEntitlement = entitlementRepository
                .findByUserIdAndFeature(withoutBudgets.userId(), Feature.BUDGETS)
                .orElseThrow();
        budgetsEntitlement.setEnabled(false);
        entitlementRepository.save(budgetsEntitlement);

        AdminDashboardResponse after = fetchDashboard(adminToken).getBody();

        // Both new users default to every feature enabled (Phase A), so
        // TRANSACTIONS goes up by 2 while BUDGETS — disabled for one of
        // them — goes up by only 1.
        assertThat(enabledCountFor(after, Feature.TRANSACTIONS) - transactionsEnabledBefore).isEqualTo(2);
        assertThat(enabledCountFor(after, Feature.BUDGETS) - budgetsEnabledBefore).isEqualTo(1);
    }

    @Test
    void aNewTransactionAndBudgetIncreaseTheirTotalsAndTheActivitySeries() {
        User admin = seedAdmin();
        String adminToken = login(admin.getEmail(), "Passw0rd1").getBody().accessToken();
        Registered user = registerAndGetToken("dashactivity");

        AdminDashboardResponse before = fetchDashboard(adminToken).getBody();
        long julyActivityBefore = before.activityOverTime().get(before.activityOverTime().size() - 1).count();

        Long categoryId = fetchSystemExpenseCategoryId(user.token());
        restTemplate.exchange(baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("25.00"), categoryId,
                        LocalDate.now(clock), "Dashboard test expense"), bearerHeaders(user.token())), Void.class);

        LocalDate month = LocalDate.now(clock).withDayOfMonth(1);
        restTemplate.exchange(baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("300.00"), null, month), bearerHeaders(user.token())),
                BudgetResponse.class);

        AdminDashboardResponse after = fetchDashboard(adminToken).getBody();
        long currentMonthActivityAfter = after.activityOverTime().get(after.activityOverTime().size() - 1).count();

        assertThat(after.summary().totalTransactions() - before.summary().totalTransactions()).isEqualTo(1);
        assertThat(after.summary().totalBudgets() - before.summary().totalBudgets()).isEqualTo(1);
        assertThat(currentMonthActivityAfter - julyActivityBefore).isEqualTo(1);
    }

    @Test
    void recentSignupsIncludesTheJustCreatedUserMostRecentFirst() {
        User admin = seedAdmin();
        String adminToken = login(admin.getEmail(), "Passw0rd1").getBody().accessToken();
        Registered user = registerAndGetToken("dashrecent");

        AdminDashboardResponse after = fetchDashboard(adminToken).getBody();

        assertThat(after.recentSignups()).extracting(RecentSignupResponse::email).first().isEqualTo(user.email());
    }

    @Test
    void aNonAdminGets403OnTheDashboardEndpoint() {
        Registered regular = registerAndGetToken("dashnonadmin");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/admin/dashboard"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(regular.token())), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Long fetchSystemExpenseCategoryId(String token) {
        ResponseEntity<PageResponse<CategoryResponse>> response = restTemplate.exchange(
                baseUrl("/api/v1/categories?size=100&type=EXPENSE"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                new ParameterizedTypeReference<PageResponse<CategoryResponse>>() {
                });
        return response.getBody().content().stream()
                .filter(CategoryResponse::system)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private record PageResponse<T>(List<T> content) {
    }
}
