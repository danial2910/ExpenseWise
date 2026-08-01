package com.expensewise.ai.controller;

import com.expensewise.ai.dto.AiConversationResponse;
import com.expensewise.ai.dto.AiConversationSummaryResponse;
import com.expensewise.ai.dto.AiMessageResponse;
import com.expensewise.ai.dto.CreateConversationRequest;
import com.expensewise.ai.dto.InsightsResponse;
import com.expensewise.ai.dto.PostMessageRequest;
import com.expensewise.ai.service.AiContextService;
import com.expensewise.ai.service.AiService;
import com.expensewise.auth.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AiService aiService;
    private final AiContextService aiContextService;

    public AiController(AiService aiService, AiContextService aiContextService) {
        this.aiService = aiService;
        this.aiContextService = aiContextService;
    }

    @GetMapping("/conversations")
    public Page<AiConversationSummaryResponse> listConversations(@AuthenticationPrincipal AuthPrincipal principal,
                                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                                   Pageable pageable) {
        return aiService.listConversations(principal.userId(), clamp(pageable));
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public AiConversationResponse createConversation(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @Valid @RequestBody CreateConversationRequest request) {
        return aiService.createConversation(principal.userId(), request);
    }

    @GetMapping("/conversations/{id}")
    public AiConversationResponse getConversation(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return aiService.getConversation(principal.userId(), id);
    }

    @PostMapping("/conversations/{id}/messages")
    public AiMessageResponse postMessage(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable Long id,
                                          @Valid @RequestBody PostMessageRequest request) {
        return aiService.postMessage(principal.userId(), id, request);
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        aiService.deleteConversation(principal.userId(), id);
    }

    @GetMapping("/insights")
    public InsightsResponse getInsights(@AuthenticationPrincipal AuthPrincipal principal) {
        return new InsightsResponse(aiContextService.buildInsights(principal.userId()));
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
