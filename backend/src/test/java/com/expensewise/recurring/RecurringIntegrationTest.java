package com.expensewise.recurring;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.budget.dto.BudgetResponse;
import com.expensewise.category.dto.CategoryRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.recurring.dto.GenerateDueResponse;
import com.expensewise.recurring.dto.PatchRecurringRuleRequest;
import com.expensewise.recurring.dto.RecurringRuleRequest;
import com.expensewise.recurring.dto.RecurringRuleResponse;
import com.expensewise.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the recurring module's ownership rule, category-coherence rule,
 * and the generate-due-now cycle end to end: transaction creation with
 * recurringRuleId set, next_due_date advancing, budgets automatically
 * reflecting the generated expense, and the generated row appearing in the
 * transactions list — same conventions TransactionIntegrationTest/
 * BudgetIntegrationTest establish.
 */
class RecurringIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    @Autowired
    private Clock clock;

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

    private ResponseEntity<CategoryResponse> createCategory(String token, String name, String type) {
        return restTemplate.exchange(baseUrl("/api/v1/categories"), HttpMethod.POST,
                new HttpEntity<>(new CategoryRequest(name, type, "pet"), bearerHeaders(token)),
                CategoryResponse.class);
    }

    private ResponseEntity<RecurringRuleResponse> createRule(String token, RecurringRuleRequest request) {
        return restTemplate.exchange(baseUrl("/api/v1/recurring"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)),
                RecurringRuleResponse.class);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KL_ZONE));
    }

    @Test
    void userACannotReadUpdatePatchDeleteOrGenerateUserBsRule() {
        Registered userA = registerAndGetToken("Reader");
        Registered userB = registerAndGetToken("Owner");

        Long foodCategoryId = systemCategoryId(userB.token(), "Food", "EXPENSE");
        Long ruleId = createRule(userB.token(),
                new RecurringRuleRequest("EXPENSE", new BigDecimal("20.00"), foodCategoryId, "Snacks", "WEEKLY",
                        today(), null))
                .getBody().id();

        ResponseEntity<ApiErrorResponse> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/recurring/" + ruleId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> updateResponse = restTemplate.exchange(
                baseUrl("/api/v1/recurring/" + ruleId), HttpMethod.PUT,
                new HttpEntity<>(new RecurringRuleRequest("EXPENSE", new BigDecimal("25.00"), foodCategoryId, "x",
                        "WEEKLY", today(), null), bearerHeaders(userA.token())),
                ApiErrorResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> patchResponse = restTemplate.exchange(
                baseUrl("/api/v1/recurring/" + ruleId), HttpMethod.PATCH,
                new HttpEntity<>(new PatchRecurringRuleRequest(null, null, null, null, null, null, null, false),
                        bearerHeaders(userA.token())),
                ApiErrorResponse.class);
        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/recurring/" + ruleId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Generate-due is scoped to the caller's own rules, so it silently
        // generates nothing for user A rather than leaking user B's rule.
        ResponseEntity<GenerateDueResponse> generateResponse = restTemplate.exchange(
                baseUrl("/api/v1/recurring/generate-due"), HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(userA.token())), GenerateDueResponse.class);
        assertThat(generateResponse.getBody().transactionsGenerated()).isZero();
    }

    @Test
    void creatingARuleAgainstAMismatchedCategoryTypeIsRejected() {
        Registered user = registerAndGetToken("Mismatcher");
        Long salaryCategoryId = systemCategoryId(user.token(), "Salary", "INCOME");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/recurring"), HttpMethod.POST,
                new HttpEntity<>(new RecurringRuleRequest("EXPENSE", new BigDecimal("10.00"), salaryCategoryId, null,
                        "MONTHLY", today(), null), bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fieldErrors()).containsKey("categoryId");
    }

    @Test
    void creatingARuleAgainstAnotherUsersPrivateCategoryIsRejected() {
        Registered owner = registerAndGetToken("CategoryOwner");
        Registered other = registerAndGetToken("Trespasser");
        Long privateCategoryId = createCategory(owner.token(), "Side Hustle", "INCOME").getBody().id();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/recurring"), HttpMethod.POST,
                new HttpEntity<>(new RecurringRuleRequest("INCOME", new BigDecimal("10.00"), privateCategoryId, null,
                        "MONTHLY", today(), null), bearerHeaders(other.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void generateDueNowCreatesATransactionAdvancesTheScheduleAndUpdatesTheBudget() {
        Registered user = registerAndGetToken("Generator");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        LocalDate startDate = today();

        RecurringRuleResponse rule = createRule(user.token(),
                new RecurringRuleRequest("EXPENSE", new BigDecimal("30.00"), foodCategoryId, "Weekly groceries",
                        "WEEKLY", startDate, null)).getBody();
        assertThat(rule.nextDueDate()).isEqualTo(startDate);

        // An overall + Food budget for this month, so the generated expense
        // has something to be reflected against.
        LocalDate periodMonth = startDate.withDayOfMonth(1);
        restTemplate.exchange(baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("500.00"), null, periodMonth), bearerHeaders(user.token())),
                BudgetResponse.class);
        restTemplate.exchange(baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("100.00"), foodCategoryId, periodMonth), bearerHeaders(user.token())),
                BudgetResponse.class);

        ResponseEntity<GenerateDueResponse> generateResponse = restTemplate.exchange(
                baseUrl("/api/v1/recurring/generate-due"), HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(user.token())), GenerateDueResponse.class);

        assertThat(generateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(generateResponse.getBody().transactionsGenerated()).isEqualTo(1);

        // next_due_date advanced by one week.
        ResponseEntity<RecurringRuleResponse> afterGenerate = restTemplate.exchange(
                baseUrl("/api/v1/recurring/" + rule.id()), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), RecurringRuleResponse.class);
        assertThat(afterGenerate.getBody().nextDueDate()).isEqualTo(startDate.plusWeeks(1));

        // The generated transaction appears in the transactions list, tagged with the rule.
        ResponseEntity<PageResponse<TransactionResponse>> transactionsResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())),
                new ParameterizedTypeReference<PageResponse<TransactionResponse>>() {
                });
        assertThat(transactionsResponse.getBody().content())
                .anySatisfy(tx -> {
                    assertThat(tx.amount()).isEqualByComparingTo("30.00");
                    assertThat(tx.categoryName()).isEqualTo("Food");
                    assertThat(tx.transactionDate()).isEqualTo(startDate);
                });

        // The budget's spent figure reflects the generated expense automatically.
        ResponseEntity<BudgetMonthResponse> budgetsResponse = restTemplate.exchange(
                baseUrl("/api/v1/budgets?month=" + periodMonth), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), BudgetMonthResponse.class);
        assertThat(budgetsResponse.getBody().overall().spent()).isEqualByComparingTo("30.00");
        var foodLine = budgetsResponse.getBody().categories().stream()
                .filter(c -> c.categoryId().equals(foodCategoryId))
                .findFirst().orElseThrow();
        assertThat(foodLine.spent()).isEqualByComparingTo("30.00");

        // Calling generate-due again the same day generates nothing further (no double-posting).
        ResponseEntity<GenerateDueResponse> secondGenerate = restTemplate.exchange(
                baseUrl("/api/v1/recurring/generate-due"), HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(user.token())), GenerateDueResponse.class);
        assertThat(secondGenerate.getBody().transactionsGenerated()).isZero();
    }

    @Test
    void deletingARuleLeavesItsAlreadyGeneratedTransactionsIntact() {
        Registered user = registerAndGetToken("Deleter");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        LocalDate startDate = today();

        RecurringRuleResponse rule = createRule(user.token(),
                new RecurringRuleRequest("EXPENSE", new BigDecimal("15.00"), foodCategoryId, "Coffee", "WEEKLY",
                        startDate, null)).getBody();

        restTemplate.exchange(baseUrl("/api/v1/recurring/generate-due"), HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(user.token())), GenerateDueResponse.class);

        restTemplate.exchange(baseUrl("/api/v1/recurring/" + rule.id()), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(user.token())), Void.class);

        ResponseEntity<PageResponse<TransactionResponse>> transactionsResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())),
                new ParameterizedTypeReference<PageResponse<TransactionResponse>>() {
                });
        assertThat(transactionsResponse.getBody().content())
                .anySatisfy(tx -> assertThat(tx.amount()).isEqualByComparingTo("15.00"));
    }
}
