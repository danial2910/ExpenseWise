package com.expensewise.ai.service;

import com.expensewise.ai.client.AiChatClient;
import com.expensewise.ai.client.AiChatMessage;
import com.expensewise.ai.dto.AiConversationResponse;
import com.expensewise.ai.dto.AiConversationSummaryResponse;
import com.expensewise.ai.dto.AiMessageResponse;
import com.expensewise.ai.dto.CreateConversationRequest;
import com.expensewise.ai.dto.PostMessageRequest;
import com.expensewise.ai.entity.AiConversation;
import com.expensewise.ai.entity.AiMessage;
import com.expensewise.ai.mapper.AiMapper;
import com.expensewise.ai.repository.AiConversationRepository;
import com.expensewise.ai.repository.AiMessageRepository;
import com.expensewise.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private AiConversationRepository conversationRepository;

    @Mock
    private AiMessageRepository messageRepository;

    @Mock
    private AiContextService aiContextService;

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private AiMapper aiMapper;

    private AiService aiService;
    private final AtomicLong idSequence = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        aiService = new AiService(conversationRepository, messageRepository, aiContextService, aiChatClient, aiMapper);

        lenient().when(conversationRepository.save(any(AiConversation.class))).thenAnswer(invocation -> {
            AiConversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) {
                conversation.setId(idSequence.incrementAndGet());
            }
            return conversation;
        });
        lenient().when(messageRepository.save(any(AiMessage.class))).thenAnswer(invocation -> {
            AiMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(idSequence.incrementAndGet());
            }
            return message;
        });
        lenient().when(aiMapper.toMessageResponse(any(AiMessage.class))).thenAnswer(invocation -> {
            AiMessage message = invocation.getArgument(0);
            return new AiMessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
        });
        lenient().when(aiMapper.toSummaryResponse(any(AiConversation.class))).thenAnswer(invocation -> {
            AiConversation conversation = invocation.getArgument(0);
            return new AiConversationSummaryResponse(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt());
        });
        lenient().when(aiMapper.toConversationResponse(any(AiConversation.class), anyList())).thenAnswer(invocation -> {
            AiConversation conversation = invocation.getArgument(0);
            List<AiMessageResponse> messages = invocation.getArgument(1);
            return new AiConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(), messages);
        });
        lenient().when(aiContextService.buildContextText(anyLong())).thenReturn("compact finance context");
        lenient().when(aiChatClient.chat(anyList())).thenReturn("This is the assistant's reply.");
    }

    private AiConversation ownConversation() {
        AiConversation conversation = new AiConversation();
        conversation.setId(50L);
        conversation.setUserId(USER_ID);
        conversation.setTitle("New conversation");
        return conversation;
    }

    private AiConversation othersConversation() {
        AiConversation conversation = new AiConversation();
        conversation.setId(60L);
        conversation.setUserId(OTHER_USER_ID);
        conversation.setTitle("Someone else's chat");
        return conversation;
    }

    // --- ownership ---

    @Test
    void getConversationHidesAnotherUsersConversationAs404() {
        when(conversationRepository.findById(60L)).thenReturn(Optional.of(othersConversation()));

        assertThatThrownBy(() -> aiService.getConversation(USER_ID, 60L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void postMessageOnAnotherUsersConversationIsRejectedAs404() {
        when(conversationRepository.findById(60L)).thenReturn(Optional.of(othersConversation()));

        assertThatThrownBy(() -> aiService.postMessage(USER_ID, 60L, new PostMessageRequest("Hello?")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(aiChatClient, never()).chat(anyList());
    }

    @Test
    void deleteConversationOnAnotherUsersConversationIsRejectedAs404() {
        when(conversationRepository.findById(60L)).thenReturn(Optional.of(othersConversation()));

        assertThatThrownBy(() -> aiService.deleteConversation(USER_ID, 60L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(conversationRepository, never()).delete(any(AiConversation.class));
    }

    // --- creation / title derivation ---

    @Test
    void createConversationWithoutAFirstMessageStaysEmptyWithADefaultTitle() {
        AiConversationResponse response = aiService.createConversation(USER_ID, new CreateConversationRequest(null));

        assertThat(response.title()).isEqualTo("New conversation");
        assertThat(response.messages()).isEmpty();
        verify(aiChatClient, never()).chat(anyList());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void createConversationWithAFirstMessagePersistsBothMessagesAndDerivesTheTitle() {
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        AiConversationResponse response = aiService.createConversation(USER_ID,
                new CreateConversationRequest("How much did I spend on dining this month?"));

        assertThat(response.title()).isEqualTo("How much did I spend on dining this month?");
        assertThat(response.messages()).hasSize(2);
        assertThat(response.messages().get(0).role()).isEqualTo("user");
        assertThat(response.messages().get(1).role()).isEqualTo("assistant");
        assertThat(response.messages().get(1).content()).isEqualTo("This is the assistant's reply.");
    }

    @Test
    void titleDerivationTruncatesAFirstMessageLongerThanTheLimit() {
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        String longMessage = "a".repeat(80);

        AiConversationResponse response = aiService.createConversation(USER_ID, new CreateConversationRequest(longMessage));

        assertThat(response.title()).hasSize(60);
        assertThat(response.title()).endsWith("…");
    }

    // --- posting a message to an existing conversation ---

    @Test
    void postMessageAppendsBothMessagesAndReturnsOnlyTheAssistantReply() {
        AiConversation existing = ownConversation();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(50L)).thenReturn(List.of());

        AiMessageResponse response = aiService.postMessage(USER_ID, 50L, new PostMessageRequest("Am I on track with my budgets?"));

        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.content()).isEqualTo("This is the assistant's reply.");

        ArgumentCaptor<AiMessage> savedMessages = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(savedMessages.capture());
        assertThat(savedMessages.getAllValues().get(0).getRole()).isEqualTo("user");
        assertThat(savedMessages.getAllValues().get(0).getContent()).isEqualTo("Am I on track with my budgets?");
        assertThat(savedMessages.getAllValues().get(1).getRole()).isEqualTo("assistant");
    }

    @Test
    void postMessageOnASecondTurnDoesNotChangeAnAlreadyDerivedTitle() {
        AiConversation existing = ownConversation();
        existing.setTitle("Dining spend trends");
        AiMessage priorUser = new AiMessage();
        priorUser.setId(1L);
        priorUser.setConversationId(50L);
        priorUser.setRole("user");
        priorUser.setContent("How much did I spend on dining?");
        AiMessage priorAssistant = new AiMessage();
        priorAssistant.setId(2L);
        priorAssistant.setConversationId(50L);
        priorAssistant.setRole("assistant");
        priorAssistant.setContent("RM 224.00 so far this month.");

        when(conversationRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(50L)).thenReturn(List.of(priorUser, priorAssistant));

        aiService.postMessage(USER_ID, 50L, new PostMessageRequest("Any suggestions to cut back?"));

        assertThat(existing.getTitle()).isEqualTo("Dining spend trends");
    }

    // --- context isolation ---

    @Test
    void theFinanceContextIsBuiltForTheRequestingUserOnly() {
        AiConversation existing = ownConversation();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(50L)).thenReturn(List.of());

        aiService.postMessage(USER_ID, 50L, new PostMessageRequest("How's my spending?"));

        verify(aiContextService).buildContextText(eq(USER_ID));
    }

    @Test
    void thePromptSentToTheModelIncludesTheSystemContextAndFullHistoryInOrder() {
        AiConversation existing = ownConversation();
        AiMessage priorUser = new AiMessage();
        priorUser.setRole("user");
        priorUser.setContent("Earlier question");
        AiMessage priorAssistant = new AiMessage();
        priorAssistant.setRole("assistant");
        priorAssistant.setContent("Earlier answer");

        when(conversationRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(50L)).thenReturn(List.of(priorUser, priorAssistant));

        aiService.postMessage(USER_ID, 50L, new PostMessageRequest("Follow-up question"));

        ArgumentCaptor<List<AiChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiChatClient).chat(promptCaptor.capture());
        List<AiChatMessage> prompt = promptCaptor.getValue();

        assertThat(prompt.get(0).role()).isEqualTo("system");
        assertThat(prompt.get(0).content()).contains("compact finance context");
        assertThat(prompt.get(1).content()).isEqualTo("Earlier question");
        assertThat(prompt.get(2).content()).isEqualTo("Earlier answer");
        assertThat(prompt.get(3).role()).isEqualTo("user");
        assertThat(prompt.get(3).content()).isEqualTo("Follow-up question");
    }
}
