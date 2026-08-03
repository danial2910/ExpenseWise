package com.expensewise.recurring.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.RequiresFeature;
import com.expensewise.recurring.dto.GenerateDueResponse;
import com.expensewise.recurring.dto.PatchRecurringRuleRequest;
import com.expensewise.recurring.dto.RecurringRuleRequest;
import com.expensewise.recurring.dto.RecurringRuleResponse;
import com.expensewise.recurring.service.RecurringGenerationService;
import com.expensewise.recurring.service.RecurringRuleService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gated under Feature.TRANSACTIONS, not a dedicated RECURRING flag —
 * recurring rules only ever produce transactions, and no RECURRING value
 * exists in the Feature enum. See DECISIONS.md.
 */
@RestController
@RequestMapping("/api/v1/recurring")
@RequiresFeature(Feature.TRANSACTIONS)
public class RecurringRuleController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RecurringRuleService recurringRuleService;
    private final RecurringGenerationService recurringGenerationService;

    public RecurringRuleController(RecurringRuleService recurringRuleService,
                                    RecurringGenerationService recurringGenerationService) {
        this.recurringRuleService = recurringRuleService;
        this.recurringGenerationService = recurringGenerationService;
    }

    @GetMapping
    public Page<RecurringRuleResponse> listRules(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PageableDefault(size = 20, sort = "nextDueDate", direction = Sort.Direction.ASC)
                                                  Pageable pageable) {
        return recurringRuleService.listRules(principal.userId(), clamp(pageable));
    }

    @GetMapping("/{id}")
    public RecurringRuleResponse getRule(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return recurringRuleService.getRule(principal.userId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringRuleResponse createRule(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody RecurringRuleRequest request) {
        return recurringRuleService.createRule(principal.userId(), request);
    }

    @PutMapping("/{id}")
    public RecurringRuleResponse updateRule(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable Long id,
                                             @Valid @RequestBody RecurringRuleRequest request) {
        return recurringRuleService.updateRule(principal.userId(), id, request);
    }

    @PatchMapping("/{id}")
    public RecurringRuleResponse patchRule(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable Long id,
                                            @Valid @RequestBody PatchRecurringRuleRequest request) {
        return recurringRuleService.patchRule(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        recurringRuleService.deleteRule(principal.userId(), id);
    }

    /**
     * Demo affordance: runs the same generation logic the daily scheduler
     * runs, scoped to the authenticated user's own due rules, so the whole
     * generate-and-advance cycle can be shown without waiting for the timer.
     */
    @PostMapping("/generate-due")
    public GenerateDueResponse generateDue(@AuthenticationPrincipal AuthPrincipal principal) {
        int generated = recurringGenerationService.generateDueForUser(principal.userId());
        return new GenerateDueResponse(generated);
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
