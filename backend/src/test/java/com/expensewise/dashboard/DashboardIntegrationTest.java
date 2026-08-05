package com.expensewise.dashboard;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.dashboard.dto.DashboardResponse;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the dashboard's aggregation against real seeded data (Postgres,
 * docker-compose port 5433), plus the security rules CLAUDE.md calls out
 * explicitly: another user's data never appears, an ADMIN gets 403 (the
 * personal dashboard is USER-only — admins have their own Admin Dashboard),
 * and an unauthenticated request gets 401.
 */
class DashboardIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private record PageResponse<T>(List<T> content) {
    }

    private record Registered(String token, Long userId) {
    }

    private Registered registerAndGetToken(String label) {
        String email = label + "+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> response = register(label, email, "Passw0rd1");
        return new Registered(response.getBody().accessToken(), response.getBody().user().id());
    }

    private Long systemCategoryId(String token, String name, String type) {
        ResponseEntity<PageResponse<CategoryResponse>> response = restTemplate.exchange(
                baseUrl("/api/v1/categories?size=100&type=" + type), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                new ParameterizedTypeReference<PageResponse<CategoryResponse>>() {
                });
        return response.getBody().content().stream()
                .filter(c -> c.system() && c.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private void createTransaction(String token, TransactionRequest request) {
        ResponseEntity<Void> response = restTemplate.exchange(baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void createBudget(String token, BudgetRequest request) {
        restTemplate.exchange(baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)), Void.class);
    }

    private ResponseEntity<DashboardResponse> getDashboard(String token) {
        return restTemplate.exchange(baseUrl("/api/v1/dashboard"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), DashboardResponse.class);
    }

    @Test
    void dashboardAggregatesSeededTransactionsAndBudgetsForTheAuthenticatedUser() {
        Registered user = registerAndGetToken("Aggregator");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long salaryCategoryId = systemCategoryId(user.token(), "Salary", "INCOME");
        LocalDate today = LocalDate.now(KL_ZONE);
        LocalDate thisMonth = today.withDayOfMonth(1);

        createBudget(user.token(), new BudgetRequest(new BigDecimal("500.00"), null, thisMonth));
        createTransaction(user.token(), new TransactionRequest("INCOME", new BigDecimal("3000.00"), salaryCategoryId, today, "Payday"));
        createTransaction(user.token(), new TransactionRequest("EXPENSE", new BigDecimal("80.00"), foodCategoryId, today, "Groceries"));
        createTransaction(user.token(), new TransactionRequest("EXPENSE", new BigDecimal("20.00"), foodCategoryId, today, "Snacks"));

        ResponseEntity<DashboardResponse> response = getDashboard(user.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardResponse body = response.getBody();
        assertThat(body.summary().thisMonthIncome()).isEqualByComparingTo("3000.00");
        assertThat(body.summary().thisMonthExpense()).isEqualByComparingTo("100.00");
        assertThat(body.summary().thisMonthBalance()).isEqualByComparingTo("2900.00");
        assertThat(body.summary().overallBalance()).isEqualByComparingTo("2900.00");

        assertThat(body.expenseByCategory()).hasSize(1);
        assertThat(body.expenseByCategory().get(0).categoryId()).isEqualTo(foodCategoryId);
        assertThat(body.expenseByCategory().get(0).amount()).isEqualByComparingTo("100.00");

        // Budget utilisation is reused straight from the budget module, not
        // recomputed — same spent/remaining figures the Budgets screen shows.
        assertThat(body.budgetUtilisation().overall().amount()).isEqualByComparingTo("500.00");
        assertThat(body.budgetUtilisation().overall().spent()).isEqualByComparingTo("100.00");
        assertThat(body.budgetUtilisation().overall().remaining()).isEqualByComparingTo("400.00");

        assertThat(body.recentTransactions()).hasSize(3);
        assertThat(body.monthlyTrend()).isNotEmpty();
        assertThat(body.monthlyTrend().get(body.monthlyTrend().size() - 1).month()).isEqualTo(thisMonth);
    }

    @Test
    void anotherUsersTransactionsAndBudgetsNeverAppearInThisUsersDashboard() {
        Registered userA = registerAndGetToken("Viewer");
        Registered userB = registerAndGetToken("Stranger");
        LocalDate today = LocalDate.now(KL_ZONE);

        Long foodCategoryIdB = systemCategoryId(userB.token(), "Food", "EXPENSE");
        createTransaction(userB.token(), new TransactionRequest("EXPENSE", new BigDecimal("999.00"), foodCategoryIdB, today, "Should not leak"));
        createBudget(userB.token(), new BudgetRequest(new BigDecimal("9999.00"), null, today.withDayOfMonth(1)));

        ResponseEntity<DashboardResponse> response = getDashboard(userA.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardResponse body = response.getBody();
        assertThat(body.summary().thisMonthExpense()).isEqualByComparingTo("0.00");
        assertThat(body.summary().overallBalance()).isEqualByComparingTo("0.00");
        assertThat(body.recentTransactions()).isEmpty();
        assertThat(body.expenseByCategory()).isEmpty();
        assertThat(body.budgetUtilisation().overall().amount()).isNull();
    }

    @Test
    void anAdminGets403OnTheDashboardEndpoint() {
        String adminEmail = "admin-dash+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd1";
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Admin User");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String adminToken = login(adminEmail, password).getBody().accessToken();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/dashboard"), HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnauthenticatedRequestGets401() {
        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/dashboard"), HttpMethod.GET, HttpEntity.EMPTY, ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
