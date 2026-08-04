package com.expensewise.budget;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.budget.dto.BudgetResponse;
import com.expensewise.budget.dto.CategoryBudgetLine;
import com.expensewise.category.dto.CategoryRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.transaction.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the budget module's ownership rule, the category-coherence rule
 * (EXPENSE-only, visible-to-caller), the (user, category-or-overall, month)
 * uniqueness rule backed by the DB's two partial unique indexes, and the
 * live spent/remaining/progress computation against real transactions —
 * same conventions TransactionIntegrationTest/CategoryIntegrationTest
 * establish for this project.
 */
class BudgetIntegrationTest extends AbstractIntegrationTest {

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

    private ResponseEntity<BudgetResponse> createBudget(String token, BudgetRequest request) {
        return restTemplate.exchange(baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)),
                BudgetResponse.class);
    }

    private void createTransaction(String token, TransactionRequest request) {
        restTemplate.exchange(baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)), Void.class);
    }

    private static LocalDate firstOfThisMonth() {
        return LocalDate.of(2026, Month.MARCH, 1);
    }

    @Test
    void userACannotReadUpdateOrDeleteUserBsBudget() {
        Registered userA = registerAndGetToken("Reader");
        Registered userB = registerAndGetToken("Owner");

        Long budgetId = createBudget(userB.token(),
                new BudgetRequest(new BigDecimal("500.00"), null, firstOfThisMonth())).getBody().id();

        ResponseEntity<ApiErrorResponse> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + budgetId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> updateResponse = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + budgetId), HttpMethod.PUT,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("600.00"), null, firstOfThisMonth()),
                        bearerHeaders(userA.token())),
                ApiErrorResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + budgetId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingABudgetOnAnIncomeCategoryIsRejected() {
        Registered user = registerAndGetToken("IncomeBudgeter");
        Long salaryCategoryId = systemCategoryId(user.token(), "Salary", "INCOME");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("200.00"), salaryCategoryId, firstOfThisMonth()),
                        bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fieldErrors()).containsKey("categoryId");
    }

    @Test
    void creatingABudgetAgainstAnotherUsersPrivateCategoryIsRejected() {
        Registered owner = registerAndGetToken("CategoryOwner");
        Registered other = registerAndGetToken("Trespasser");

        Long privateCategoryId = createCategory(owner.token(), "Side Hustle Expenses", "EXPENSE").getBody().id();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("200.00"), privateCategoryId, firstOfThisMonth()),
                        bearerHeaders(other.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void aDuplicateBudgetForTheSameUserCategoryAndMonthIsRejected() {
        Registered user = registerAndGetToken("Duplicator");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        LocalDate month = firstOfThisMonth();

        createBudget(user.token(), new BudgetRequest(new BigDecimal("1000.00"), null, month));

        ResponseEntity<BudgetResponse> first = createBudget(user.token(),
                new BudgetRequest(new BigDecimal("300.00"), foodCategoryId, month));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiErrorResponse> second = restTemplate.exchange(
                baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("400.00"), foodCategoryId, month),
                        bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void aCategoryBudgetCannotBeSetBeforeAnOverallBudgetExistsForTheMonth() {
        Registered user = registerAndGetToken("NoOverallYet");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("50.00"), foodCategoryId, firstOfThisMonth()),
                        bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void categoryBudgetsCannotTogetherExceedTheOverallBudget() {
        Registered user = registerAndGetToken("CapEnforcer");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long transportCategoryId = systemCategoryId(user.token(), "Transport", "EXPENSE");
        LocalDate month = firstOfThisMonth();

        createBudget(user.token(), new BudgetRequest(new BigDecimal("100.00"), null, month));
        createBudget(user.token(), new BudgetRequest(new BigDecimal("70.00"), foodCategoryId, month));

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("40.00"), transportCategoryId, month),
                        bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void theOverallBudgetCannotBeLoweredBelowTheSumOfExistingCategoryBudgets() {
        Registered user = registerAndGetToken("OverallLowerer");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        LocalDate month = firstOfThisMonth();

        Long overallId = createBudget(user.token(), new BudgetRequest(new BigDecimal("200.00"), null, month))
                .getBody().id();
        createBudget(user.token(), new BudgetRequest(new BigDecimal("150.00"), foodCategoryId, month));

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + overallId), HttpMethod.PUT,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("100.00"), null, month), bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void theOverallBudgetCannotBeDeletedWhileCategoryBudgetsStillExistForTheMonth() {
        Registered user = registerAndGetToken("OverallDeleter");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        LocalDate month = firstOfThisMonth();

        Long overallId = createBudget(user.token(), new BudgetRequest(new BigDecimal("200.00"), null, month))
                .getBody().id();
        createBudget(user.token(), new BudgetRequest(new BigDecimal("50.00"), foodCategoryId, month));

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + overallId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(user.token())), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aPeriodMonthThatIsNotTheFirstOfTheMonthIsRejected() {
        Registered user = registerAndGetToken("MidMonth");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("200.00"), null, firstOfThisMonth().plusDays(4)),
                        bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("periodMonth");
    }

    @Test
    void theMonthViewComputesSpentRemainingAndExceededAgainstRealExpenseTransactions() {
        Registered user = registerAndGetToken("Tracker");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long salaryCategoryId = systemCategoryId(user.token(), "Salary", "INCOME");
        LocalDate month = firstOfThisMonth();
        LocalDate today = LocalDate.of(2026, Month.MARCH, 15);

        createBudget(user.token(), new BudgetRequest(new BigDecimal("500.00"), null, month));
        createBudget(user.token(), new BudgetRequest(new BigDecimal("50.00"), foodCategoryId, month));

        createTransaction(user.token(), new TransactionRequest("EXPENSE", new BigDecimal("60.00"), foodCategoryId, today, "Groceries"));
        // An income transaction must never count as spending against a budget.
        createTransaction(user.token(), new TransactionRequest("INCOME", new BigDecimal("2000.00"), salaryCategoryId, today, "Payday"));

        ResponseEntity<BudgetMonthResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/budgets?month=" + month), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), BudgetMonthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BudgetMonthResponse body = response.getBody();
        assertThat(body.overall().spent()).isEqualByComparingTo("60.00");
        assertThat(body.overall().remaining()).isEqualByComparingTo("440.00");
        assertThat(body.overall().exceeded()).isFalse();

        CategoryBudgetLine foodLine = body.categories().stream()
                .filter(c -> c.categoryId().equals(foodCategoryId))
                .findFirst().orElseThrow();
        assertThat(foodLine.spent()).isEqualByComparingTo("60.00");
        assertThat(foodLine.remaining()).isEqualByComparingTo("-10.00");
        assertThat(foodLine.exceeded()).isTrue();

        // A category with no budget set still appears, with spending shown
        // but no limit — matches the design's "No budget set" row.
        CategoryBudgetLine unbudgeted = body.categories().stream()
                .filter(c -> !c.categoryId().equals(foodCategoryId))
                .findFirst().orElseThrow();
        assertThat(unbudgeted.amount()).isNull();
        assertThat(unbudgeted.remaining()).isNull();
    }

    @Test
    void aUserCanCreateEditAndDeleteTheirOwnBudget() {
        Registered user = registerAndGetToken("Editor");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        LocalDate month = firstOfThisMonth();

        createBudget(user.token(), new BudgetRequest(new BigDecimal("1000.00"), null, month));

        BudgetResponse created = createBudget(user.token(),
                new BudgetRequest(new BigDecimal("300.00"), foodCategoryId, month)).getBody();
        assertThat(created.categoryName()).isEqualTo("Food");

        ResponseEntity<BudgetResponse> updateResponse = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + created.id()), HttpMethod.PUT,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("350.00"), foodCategoryId, month),
                        bearerHeaders(user.token())),
                BudgetResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().amount()).isEqualByComparingTo("350.00");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + created.id()), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(user.token())), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiErrorResponse> getAfterDelete = restTemplate.exchange(
                baseUrl("/api/v1/budgets/" + created.id()), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), ApiErrorResponse.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
