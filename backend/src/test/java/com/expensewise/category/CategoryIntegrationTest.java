package com.expensewise.category;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.category.dto.CategoryRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the category module's visibility rule (system + own custom
 * categories only), the ownership checks required by CLAUDE.md (user A
 * cannot touch user B's records, system categories are read-only to
 * everyone), and the DB-level RESTRICT on transactions.category_id.
 */
class CategoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record PageResponse<T>(List<T> content) {
    }

    private String registerAndGetToken(String label) {
        String email = label + "+" + System.nanoTime() + "@example.com";
        return register(label, email, "Passw0rd1").getBody().accessToken();
    }

    private ResponseEntity<PageResponse<CategoryResponse>> listCategories(String token) {
        return restTemplate.exchange(baseUrl("/api/v1/categories?size=100"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                new org.springframework.core.ParameterizedTypeReference<PageResponse<CategoryResponse>>() {
                });
    }

    private ResponseEntity<CategoryResponse> createCategory(String token, String name, String type) {
        return restTemplate.exchange(baseUrl("/api/v1/categories"), HttpMethod.POST,
                new HttpEntity<>(new CategoryRequest(name, type, "pet"), bearerHeaders(token)),
                CategoryResponse.class);
    }

    @Test
    void aUserSeesSystemCategoriesPlusOnlyTheirOwnCustomCategories() {
        String userAToken = registerAndGetToken("UserA");
        String userBToken = registerAndGetToken("UserB");

        createCategory(userAToken, "Pet Care", "EXPENSE");
        createCategory(userBToken, "Side Hustle", "INCOME");

        List<CategoryResponse> visibleToA = listCategories(userAToken).getBody().content();

        assertThat(visibleToA).extracting(CategoryResponse::name).contains("Pet Care", "Food");
        assertThat(visibleToA).extracting(CategoryResponse::name).doesNotContain("Side Hustle");
        assertThat(visibleToA).filteredOn(CategoryResponse::system).isNotEmpty();
    }

    @Test
    void userACannotReadUpdateOrDeleteUserBsCategory() {
        String userAToken = registerAndGetToken("Reader");
        String userBToken = registerAndGetToken("Owner");

        Long categoryId = createCategory(userBToken, "Side Hustle", "INCOME").getBody().id();

        ResponseEntity<ApiErrorResponse> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/categories/" + categoryId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userAToken)), ApiErrorResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> updateResponse = restTemplate.exchange(
                baseUrl("/api/v1/categories/" + categoryId), HttpMethod.PUT,
                new HttpEntity<>(new CategoryRequest("Renamed", "INCOME", "pet"), bearerHeaders(userAToken)),
                ApiErrorResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/categories/" + categoryId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userAToken)), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nobodyCanEditOrDeleteASystemCategory() {
        String token = registerAndGetToken("RegularUser");
        Long systemCategoryId = jdbcTemplate.queryForObject(
                "select id from categories where is_system = true and name = 'Food' and type = 'EXPENSE'",
                Long.class);

        ResponseEntity<ApiErrorResponse> updateResponse = restTemplate.exchange(
                baseUrl("/api/v1/categories/" + systemCategoryId), HttpMethod.PUT,
                new HttpEntity<>(new CategoryRequest("Renamed", "EXPENSE", "pet"), bearerHeaders(token)),
                ApiErrorResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/categories/" + systemCategoryId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingADuplicateNameAndTypeForTheSameUserIsRejected() {
        String token = registerAndGetToken("Duplicator");
        createCategory(token, "Book Club", "EXPENSE");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/categories"), HttpMethod.POST,
                new HttpEntity<>(new CategoryRequest("Book Club", "EXPENSE", "pet"), bearerHeaders(token)),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void deletingACategoryReferencedByATransactionFailsCleanly() {
        ResponseEntity<LoginResponse> registerResponse = register("Referenced", "ref+" + System.nanoTime() + "@example.com", "Passw0rd1");
        Long userId = registerResponse.getBody().user().id();
        String userToken = registerResponse.getBody().accessToken();

        CategoryResponse category = createCategory(userToken, "Referenced Category", "EXPENSE").getBody();

        jdbcTemplate.update("""
                insert into transactions (user_id, category_id, type, amount, transaction_date)
                values (?, ?, 'EXPENSE', 10.00, current_date)
                """, userId, category.id());

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/categories/" + category.id()), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userToken)), ApiErrorResponse.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deleteResponse.getBody().error()).isEqualTo("CATEGORY_IN_USE");
    }
}
