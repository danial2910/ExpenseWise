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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiService {

    private static final String SYSTEM_PROMPT = """
            You are the ExpenseWise AI Assistant, a personal-finance helper built into the \
            ExpenseWise app. Answer using ONLY the financial data provided below, which \
            belongs exclusively to the user you are talking to right now — never claim to \
            know or discuss any other user's data. Give general, educational financial \
            information, not licensed financial or investment advice. Whenever you give a \
            recommendation or suggestion, include a brief one-line disclaimer that this is \
            general information, not professional financial advice. Keep replies concise \
            and practical.
            """;
    private static final int TITLE_MAX_LENGTH = 60;
    private static final String DEFAULT_TITLE = "New conversation";

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiContextService aiContextService;
    private final AiChatClient aiChatClient;
    private final AiMapper aiMapper;

    public AiService(AiConversationRepository conversationRepository,
                      AiMessageRepository messageRepository,
                      AiContextService aiContextService,
                      AiChatClient aiChatClient,
                      AiMapper aiMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.aiContextService = aiContextService;
        this.aiChatClient = aiChatClient;
        this.aiMapper = aiMapper;
    }

    @Transactional(readOnly = true)
    public Page<AiConversationSummaryResponse> listConversations(Long userId, Pageable pageable) {
        return conversationRepository.findByUserId(userId, pageable).map(aiMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public AiConversationResponse getConversation(Long userId, Long id) {
        AiConversation conversation = findOwnedOrThrow(userId, id);
        List<AiMessageResponse> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(id).stream()
                .map(aiMapper::toMessageResponse)
                .toList();
        return aiMapper.toConversationResponse(conversation, messages);
    }

    @Transactional
    public AiConversationResponse createConversation(Long userId, CreateConversationRequest request) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(DEFAULT_TITLE);
        AiConversation saved = conversationRepository.save(conversation);

        List<AiMessageResponse> messages = List.of();
        if (request.firstMessage() != null && !request.firstMessage().isBlank()) {
            messages = exchangeMessages(userId, saved, request.firstMessage());
        }
        return aiMapper.toConversationResponse(saved, messages);
    }

    @Transactional
    public AiMessageResponse postMessage(Long userId, Long conversationId, PostMessageRequest request) {
        AiConversation conversation = findOwnedOrThrow(userId, conversationId);
        List<AiMessageResponse> exchange = exchangeMessages(userId, conversation, request.content());
        return exchange.get(exchange.size() - 1);
    }

    @Transactional
    public void deleteConversation(Long userId, Long id) {
        AiConversation conversation = findOwnedOrThrow(userId, id);
        conversationRepository.delete(conversation);
    }

    /**
     * Appends the user message, builds this user's finance context, calls
     * the model once (non-streaming), then appends the assistant reply —
     * all within the caller's transaction, per CLAUDE.md. Returns
     * [userMessage, assistantMessage].
     */
    private List<AiMessageResponse> exchangeMessages(Long userId, AiConversation conversation, String userContent) {
        List<AiMessage> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        if (history.isEmpty()) {
            conversation.setTitle(deriveTitle(userContent));
        }

        AiMessage userMessage = new AiMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole("user");
        userMessage.setContent(userContent);
        AiMessage savedUserMessage = messageRepository.save(userMessage);

        List<AiChatMessage> prompt = new ArrayList<>();
        prompt.add(new AiChatMessage("system", SYSTEM_PROMPT + "\n" + aiContextService.buildContextText(userId)));
        for (AiMessage past : history) {
            prompt.add(new AiChatMessage(past.getRole(), past.getContent()));
        }
        prompt.add(new AiChatMessage("user", userContent));

        String replyContent = aiChatClient.chat(prompt);

        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(replyContent);
        AiMessage savedAssistantMessage = messageRepository.save(assistantMessage);

        return List.of(aiMapper.toMessageResponse(savedUserMessage), aiMapper.toMessageResponse(savedAssistantMessage));
    }

    private String deriveTitle(String firstMessage) {
        String trimmed = firstMessage.strip();
        if (trimmed.length() <= TITLE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, TITLE_MAX_LENGTH - 1).stripTrailing() + "…";
    }

    /**
     * Only the caller's own conversations can be read, posted to, or
     * deleted — another user's conversation is reported as 404, matching
     * every other module's ownership convention.
     */
    private AiConversation findOwnedOrThrow(Long userId, Long id) {
        AiConversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!conversation.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        return conversation;
    }
}
