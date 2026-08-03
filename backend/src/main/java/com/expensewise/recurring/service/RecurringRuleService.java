package com.expensewise.recurring.service;

import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.exception.InvalidRecurringCategoryException;
import com.expensewise.exception.InvalidRecurringPeriodException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.recurring.dto.PatchRecurringRuleRequest;
import com.expensewise.recurring.dto.RecurringRuleRequest;
import com.expensewise.recurring.dto.RecurringRuleResponse;
import com.expensewise.recurring.entity.RecurringRule;
import com.expensewise.recurring.mapper.RecurringRuleMapper;
import com.expensewise.recurring.repository.RecurringRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class RecurringRuleService {

    private final RecurringRuleRepository recurringRuleRepository;
    private final CategoryRepository categoryRepository;
    private final RecurringRuleMapper recurringRuleMapper;

    public RecurringRuleService(RecurringRuleRepository recurringRuleRepository,
                                 CategoryRepository categoryRepository,
                                 RecurringRuleMapper recurringRuleMapper) {
        this.recurringRuleRepository = recurringRuleRepository;
        this.categoryRepository = categoryRepository;
        this.recurringRuleMapper = recurringRuleMapper;
    }

    @Transactional(readOnly = true)
    public Page<RecurringRuleResponse> listRules(Long userId, Pageable pageable) {
        return recurringRuleRepository.findByUserId(userId, pageable)
                .map(rule -> toResponse(rule, categoryRepository.findById(rule.getCategoryId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public RecurringRuleResponse getRule(Long userId, Long id) {
        RecurringRule rule = findOwnedOrThrow(userId, id);
        return toResponse(rule, categoryRepository.findById(rule.getCategoryId()).orElse(null));
    }

    @Transactional
    public RecurringRuleResponse createRule(Long userId, RecurringRuleRequest request) {
        requireEndAfterStart(request.startDate(), request.endDate());
        Category category = requireCoherentCategory(userId, request.categoryId(), request.type());

        RecurringRule rule = new RecurringRule();
        rule.setUserId(userId);
        rule.setCategoryId(request.categoryId());
        rule.setType(request.type());
        rule.setAmount(request.amount());
        rule.setDescription(request.description());
        rule.setFrequency(request.frequency());
        rule.setStartDate(request.startDate());
        rule.setEndDate(request.endDate());
        rule.setNextDueDate(request.startDate());
        rule.setActive(true);

        RecurringRule saved = recurringRuleRepository.save(rule);
        return toResponse(saved, category);
    }

    /**
     * A full edit changes the rule's terms for FUTURE generation only — it
     * never rewrites transactions already created. nextDueDate is left as
     * the schedule currently tracks it, except when the new start date now
     * falls after it (the schedule can't be due before the rule starts).
     * isActive is untouched here; pause/resume is a PATCH-only affordance.
     */
    @Transactional
    public RecurringRuleResponse updateRule(Long userId, Long id, RecurringRuleRequest request) {
        RecurringRule rule = findOwnedOrThrow(userId, id);
        requireEndAfterStart(request.startDate(), request.endDate());
        Category category = requireCoherentCategory(userId, request.categoryId(), request.type());

        rule.setCategoryId(request.categoryId());
        rule.setType(request.type());
        rule.setAmount(request.amount());
        rule.setDescription(request.description());
        rule.setFrequency(request.frequency());
        rule.setStartDate(request.startDate());
        rule.setEndDate(request.endDate());
        if (rule.getNextDueDate().isBefore(request.startDate())) {
            rule.setNextDueDate(request.startDate());
        }

        return toResponse(rule, category);
    }

    @Transactional
    public RecurringRuleResponse patchRule(Long userId, Long id, PatchRecurringRuleRequest request) {
        RecurringRule rule = findOwnedOrThrow(userId, id);

        String type = request.type() != null ? request.type() : rule.getType();
        Long categoryId = request.categoryId() != null ? request.categoryId() : rule.getCategoryId();
        LocalDate startDate = request.startDate() != null ? request.startDate() : rule.getStartDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : rule.getEndDate();

        requireEndAfterStart(startDate, endDate);
        Category category = requireCoherentCategory(userId, categoryId, type);

        rule.setType(type);
        rule.setCategoryId(categoryId);
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        if (request.amount() != null) {
            rule.setAmount(request.amount());
        }
        if (request.description() != null) {
            rule.setDescription(request.description());
        }
        if (request.frequency() != null) {
            rule.setFrequency(request.frequency());
        }
        if (rule.getNextDueDate().isBefore(startDate)) {
            rule.setNextDueDate(startDate);
        }
        if (request.isActive() != null) {
            rule.setActive(request.isActive());
        }

        return toResponse(rule, category);
    }

    @Transactional
    public void deleteRule(Long userId, Long id) {
        RecurringRule rule = findOwnedOrThrow(userId, id);
        recurringRuleRepository.delete(rule);
    }

    private void requireEndAfterStart(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidRecurringPeriodException("End date must be on or after the start date");
        }
    }

    /**
     * The rule's type must equal the referenced category's type, and the
     * category must be visible to the caller (a system category, or one
     * they own) — mirrors TransactionService.requireCoherentCategory.
     */
    private Category requireCoherentCategory(Long userId, Long categoryId, String type) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidRecurringCategoryException("Category not found"));
        boolean visible = category.getUserId() == null || category.getUserId().equals(userId);
        if (!visible) {
            throw new InvalidRecurringCategoryException("Category not found");
        }
        if (!category.getType().equals(type)) {
            throw new InvalidRecurringCategoryException("Type must match the category's type");
        }
        return category;
    }

    private RecurringRuleResponse toResponse(RecurringRule rule, Category category) {
        return recurringRuleMapper.toResponse(rule, category);
    }

    /**
     * Only the caller's own rules can be read or mutated — another user's
     * rule is reported as 404, not 403 (same convention as
     * TransactionService/BudgetService).
     */
    private RecurringRule findOwnedOrThrow(Long userId, Long id) {
        RecurringRule rule = recurringRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));
        if (!rule.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Recurring rule not found");
        }
        return rule;
    }
}
