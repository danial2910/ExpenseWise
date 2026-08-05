package com.expensewise.report;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.admin.dto.AdminUpdateUserAccessRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.entitlement.Feature;
import com.expensewise.report.dto.ReportResponse;
import com.expensewise.transaction.dto.TransactionRequest;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers report generation against real seeded data (Postgres, docker-compose
 * port 5433) plus the security rules CLAUDE.md and this module's task call
 * out explicitly: scoped to the authenticated user, an ADMIN gets 403, a USER
 * without the REPORTS entitlement gets 403, and unauthenticated gets 401.
 */
class ReportIntegrationTest extends AbstractIntegrationTest {

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

    private String registerAdminAndLogin() {
        String adminEmail = "admin-report+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd1";
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Admin User");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        return login(adminEmail, password).getBody().accessToken();
    }

    /** Disables Feature.REPORTS for the given user via the existing admin endpoint — no direct repository access. */
    private void disableReportsFor(Long userId, String adminToken) {
        Set<Feature> withoutReports = EnumSet.allOf(Feature.class);
        withoutReports.remove(Feature.REPORTS);
        restTemplate.exchange(baseUrl("/api/v1/admin/users/" + userId + "/access"), HttpMethod.PUT,
                new HttpEntity<>(new AdminUpdateUserAccessRequest("USER", true, withoutReports),
                        bearerHeaders(adminToken)),
                Void.class);
    }

    @Test
    void monthlyReportAggregatesSeededDataScopedToTheAuthenticatedUser() {
        Registered user = registerAndGetToken("ReportUser");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long salaryCategoryId = systemCategoryId(user.token(), "Salary", "INCOME");
        LocalDate date = LocalDate.of(2026, Month.MARCH, 10);

        createBudget(user.token(), new BudgetRequest(new BigDecimal("500.00"), null, LocalDate.of(2026, Month.MARCH, 1)));
        createTransaction(user.token(), new TransactionRequest("INCOME", new BigDecimal("3000.00"), salaryCategoryId, date, "Payday"));
        createTransaction(user.token(), new TransactionRequest("EXPENSE", new BigDecimal("80.00"), foodCategoryId, date, "Groceries"));

        ResponseEntity<ReportResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/reports?type=monthly&year=2026&month=3"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), ReportResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ReportResponse body = response.getBody();
        assertThat(body.totalIncome()).isEqualByComparingTo("3000.00");
        assertThat(body.totalExpense()).isEqualByComparingTo("80.00");
        assertThat(body.netBalance()).isEqualByComparingTo("2920.00");
        assertThat(body.transactions()).hasSize(2);
        assertThat(body.categoryBreakdown()).hasSize(1);
        assertThat(body.budgetSummary().totalBudgeted()).isEqualByComparingTo("500.00");
    }

    @Test
    void anotherUsersTransactionsNeverAppearInThisUsersReport() {
        Registered userA = registerAndGetToken("ReportViewer");
        Registered userB = registerAndGetToken("ReportStranger");
        Long foodCategoryIdB = systemCategoryId(userB.token(), "Food", "EXPENSE");
        createTransaction(userB.token(), new TransactionRequest("EXPENSE", new BigDecimal("999.00"), foodCategoryIdB,
                LocalDate.of(2026, Month.MARCH, 5), "Should not leak"));

        ResponseEntity<ReportResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/reports?type=monthly&year=2026&month=3"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userA.token())), ReportResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalExpense()).isEqualByComparingTo("0.00");
        assertThat(response.getBody().transactions()).isEmpty();
    }

    @Test
    void pdfDownloadReturnsTheCorrectContentTypeAndANonEmptyBody() {
        Registered user = registerAndGetToken("PdfDownloader");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        createTransaction(user.token(), new TransactionRequest("EXPENSE", new BigDecimal("40.00"), foodCategoryId,
                LocalDate.of(2026, Month.MARCH, 5), "Snacks"));

        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl("/api/v1/reports/download?type=monthly&year=2026&month=3&format=pdf"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).hasToString("application/pdf");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains(".pdf");
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void excelDownloadReturnsTheCorrectContentTypeAndANonEmptyBody() {
        Registered user = registerAndGetToken("ExcelDownloader");

        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl("/api/v1/reports/download?type=yearly&year=2026&format=excel"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .hasToString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains(".xlsx");
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void anAdminGets403OnTheReportsEndpoint() {
        String adminToken = registerAdminAndLogin();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/reports?type=monthly&year=2026&month=3"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aUserWithoutTheReportsEntitlementGets403() {
        Registered user = registerAndGetToken("NoReportsAccess");
        String adminToken = registerAdminAndLogin();
        disableReportsFor(user.userId(), adminToken);

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/reports?type=monthly&year=2026&month=3"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("FEATURE_DISABLED");
    }

    @Test
    void anUnauthenticatedRequestGets401() {
        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/reports?type=monthly&year=2026&month=3"), HttpMethod.GET,
                HttpEntity.EMPTY, ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
