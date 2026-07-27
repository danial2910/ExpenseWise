package com.expensewise.transaction;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.category.dto.CategoryRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.transaction.dto.TransactionRequest;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.dto.TransactionSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the transaction module's ownership rule (user A cannot touch user
 * B's transactions), the category-coherence rule (type must match the
 * category's type, and the category must be visible to the caller), and
 * the income/expense/balance summary aggregation — the same conventions
 * CategoryIntegrationTest establishes for this project.
 */
class TransactionIntegrationTest extends AbstractIntegrationTest {

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

    private ResponseEntity<TransactionResponse> createTransaction(String token, TransactionRequest request) {
        return restTemplate.exchange(baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)),
                TransactionResponse.class);
    }

    @Test
    void userACannotReadUpdateOrDeleteUserBsTransaction() {
        Registered userA = registerAndGetToken("Reader");
        Registered userB = registerAndGetToken("Owner");

        Long foodCategoryId = systemCategoryId(userB.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(userB.token(),
                new TransactionRequest("EXPENSE", new BigDecimal("15.00"), foodCategoryId, LocalDate.now(), "Lunch"))
                .getBody().id();

        ResponseEntity<ApiErrorResponse> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> updateResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId), HttpMethod.PUT,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("20.00"), foodCategoryId, LocalDate.now(), "Edited"),
                        bearerHeaders(userA.token())),
                ApiErrorResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingATransactionWithATypeThatDoesNotMatchTheCategorysTypeIsRejected() {
        Registered user = registerAndGetToken("Mismatcher");
        Long salaryCategoryId = systemCategoryId(user.token(), "Salary", "INCOME");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("10.00"), salaryCategoryId, LocalDate.now(), null),
                        bearerHeaders(user.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fieldErrors()).containsKey("categoryId");
    }

    @Test
    void creatingATransactionAgainstAnotherUsersPrivateCategoryIsRejected() {
        Registered owner = registerAndGetToken("CategoryOwner");
        Registered other = registerAndGetToken("Trespasser");

        Long privateCategoryId = createCategory(owner.token(), "Side Hustle", "INCOME").getBody().id();

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(new TransactionRequest("INCOME", new BigDecimal("10.00"), privateCategoryId, LocalDate.now(), null),
                        bearerHeaders(other.token())),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void summaryTotalsAreCorrectForAMixOfIncomeAndExpenseRows() {
        Registered user = registerAndGetToken("Summarizer");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long salaryCategoryId = systemCategoryId(user.token(), "Salary", "INCOME");

        LocalDate today = LocalDate.now();
        createTransaction(user.token(), new TransactionRequest("INCOME", new BigDecimal("2000.00"), salaryCategoryId, today, "Payday"));
        createTransaction(user.token(), new TransactionRequest("EXPENSE", new BigDecimal("50.00"), foodCategoryId, today, "Groceries"));
        createTransaction(user.token(), new TransactionRequest("EXPENSE", new BigDecimal("25.50"), foodCategoryId, today, "Dinner"));

        ResponseEntity<TransactionSummaryResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/transactions/summary?from=" + today + "&to=" + today), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), TransactionSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalIncome()).isEqualByComparingTo("2000.00");
        assertThat(response.getBody().totalExpense()).isEqualByComparingTo("75.50");
        assertThat(response.getBody().balance()).isEqualByComparingTo("1924.50");
    }

    @Test
    void aUserCanCreateEditAndDeleteTheirOwnTransaction() {
        Registered user = registerAndGetToken("Editor");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");

        TransactionResponse created = createTransaction(user.token(),
                new TransactionRequest("EXPENSE", new BigDecimal("12.00"), foodCategoryId, LocalDate.now(), "Coffee"))
                .getBody();
        assertThat(created.categoryName()).isEqualTo("Food");

        ResponseEntity<TransactionResponse> updateResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + created.id()), HttpMethod.PUT,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("15.00"), foodCategoryId, LocalDate.now(), "Coffee and cake"),
                        bearerHeaders(user.token())),
                TransactionResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().description()).isEqualTo("Coffee and cake");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + created.id()), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(user.token())), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiErrorResponse> getAfterDelete = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + created.id()), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), ApiErrorResponse.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
