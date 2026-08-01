package com.expensewise.ai;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.ai.client.AiChatClient;
import com.expensewise.ai.client.AiChatMessage;
import com.expensewise.ai.dto.AiConversationResponse;
import com.expensewise.ai.dto.AiConversationSummaryResponse;
import com.expensewise.ai.dto.AiMessageResponse;
import com.expensewise.ai.dto.CreateConversationRequest;
import com.expensewise.ai.dto.InsightsResponse;
import com.expensewise.ai.dto.PostMessageRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.transaction.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * The AI chat model is entirely mocked here (@MockBean AiChatClient) — no
 * real Groq call is ever made in tests, matching CLAUDE.md's "do not assert
 * on AI response content" rule. These tests assert behaviour: a user+
 * assistant message pair is persisted, the reply is returned, ownership is
 * enforced, and the finance context passed to the model contains only the
 * requesting user's own data.
 */
class AiIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private AiChatClient aiChatClient;

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

    private ResponseEntity<AiConversationResponse> createConversation(String token, CreateConversationRequest request) {
        return restTemplate.exchange(baseUrl("/api/v1/ai/conversations"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)), AiConversationResponse.class);
    }

    @Test
    void userACannotReadPostToOrDeleteUserBsConversation() {
        when(aiChatClient.chat(anyList())).thenReturn("Mocked reply");
        Registered userA = registerAndGetToken("Reader");
        Registered userB = registerAndGetToken("Owner");

        Long conversationId = createConversation(userB.token(), new CreateConversationRequest("Hello")).getBody().id();

        ResponseEntity<ApiErrorResponse> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations/" + conversationId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> postResponse = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations/" + conversationId + "/messages"), HttpMethod.POST,
                new HttpEntity<>(new PostMessageRequest("Trying to sneak in"), bearerHeaders(userA.token())),
                ApiErrorResponse.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations/" + conversationId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingAConversationWithAFirstMessagePersistsBothMessagesAndReturnsTheMockedReply() {
        when(aiChatClient.chat(anyList())).thenReturn("Here is your spending summary.");
        Registered user = registerAndGetToken("Chatter");

        ResponseEntity<AiConversationResponse> response = createConversation(user.token(),
                new CreateConversationRequest("How much did I spend on dining this month?"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AiConversationResponse conversation = response.getBody();
        assertThat(conversation.title()).isEqualTo("How much did I spend on dining this month?");
        assertThat(conversation.messages()).hasSize(2);
        assertThat(conversation.messages().get(0).role()).isEqualTo("user");
        assertThat(conversation.messages().get(1).role()).isEqualTo("assistant");
        assertThat(conversation.messages().get(1).content()).isEqualTo("Here is your spending summary.");
    }

    @Test
    void postingAMessageToAnExistingConversationAppendsBothMessagesAndReturnsTheAssistantReply() {
        when(aiChatClient.chat(anyList())).thenReturn("First reply", "Second reply");
        Registered user = registerAndGetToken("Continuer");

        Long conversationId = createConversation(user.token(), new CreateConversationRequest("First question")).getBody().id();

        ResponseEntity<AiMessageResponse> postResponse = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations/" + conversationId + "/messages"), HttpMethod.POST,
                new HttpEntity<>(new PostMessageRequest("Follow-up question"), bearerHeaders(user.token())),
                AiMessageResponse.class);

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postResponse.getBody().role()).isEqualTo("assistant");
        assertThat(postResponse.getBody().content()).isEqualTo("Second reply");

        ResponseEntity<AiConversationResponse> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations/" + conversationId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), AiConversationResponse.class);
        assertThat(getResponse.getBody().messages()).hasSize(4);
    }

    @Test
    void aUserCanListAndDeleteTheirOwnConversation() {
        when(aiChatClient.chat(anyList())).thenReturn("Reply");
        Registered user = registerAndGetToken("Lister");
        Long conversationId = createConversation(user.token(), new CreateConversationRequest("Hi")).getBody().id();

        ResponseEntity<PageResponse<AiConversationSummaryResponse>> listResponse = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())),
                new ParameterizedTypeReference<PageResponse<AiConversationSummaryResponse>>() {
                });
        assertThat(listResponse.getBody().content()).extracting(AiConversationSummaryResponse::id).contains(conversationId);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations/" + conversationId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(user.token())), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiErrorResponse> getAfterDelete = restTemplate.exchange(
                baseUrl("/api/v1/ai/conversations/" + conversationId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), ApiErrorResponse.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theFinanceContextSentToTheModelContainsOnlyTheRequestingUsersData() {
        ArgumentCaptor<List<AiChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        when(aiChatClient.chat(promptCaptor.capture())).thenReturn("Reply");

        Registered userA = registerAndGetToken("ContextA");
        Registered userB = registerAndGetToken("ContextB");

        Long foodCategoryIdA = systemCategoryId(userA.token(), "Food", "EXPENSE");
        Long foodCategoryIdB = systemCategoryId(userB.token(), "Food", "EXPENSE");
        LocalDate today = LocalDate.now();

        restTemplate.exchange(baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("42.42"), foodCategoryIdA, today, "User A's lunch"),
                        bearerHeaders(userA.token())),
                Void.class);
        restTemplate.exchange(baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("999.99"), foodCategoryIdB, today, "User B's secret spending"),
                        bearerHeaders(userB.token())),
                Void.class);

        createConversation(userA.token(), new CreateConversationRequest("How much have I spent?"));

        List<AiChatMessage> prompt = promptCaptor.getValue();
        String systemContext = prompt.get(0).content();

        assertThat(systemContext).contains("42.42");
        assertThat(systemContext).doesNotContain("999.99");
    }

    @Test
    void insightsReflectTheCallersOwnBudgetStatus() {
        Registered user = registerAndGetToken("Insighter");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        LocalDate month = LocalDate.now().withDayOfMonth(1);

        restTemplate.exchange(baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("100.00"), null, month), bearerHeaders(user.token())),
                Void.class);
        restTemplate.exchange(baseUrl("/api/v1/budgets"), HttpMethod.POST,
                new HttpEntity<>(new BudgetRequest(new BigDecimal("20.00"), foodCategoryId, month), bearerHeaders(user.token())),
                Void.class);
        restTemplate.exchange(baseUrl("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("30.00"), foodCategoryId, LocalDate.now(), "Over"),
                        bearerHeaders(user.token())),
                Void.class);

        ResponseEntity<InsightsResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/ai/insights"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), InsightsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().insights()).isNotEmpty();
        assertThat(response.getBody().insights().get(0).severity()).isEqualTo("CRITICAL");
    }
}
