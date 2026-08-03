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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringRuleServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private RecurringRuleRepository recurringRuleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RecurringRuleMapper recurringRuleMapper;

    private RecurringRuleService recurringRuleService;

    @BeforeEach
    void setUp() {
        recurringRuleService = new RecurringRuleService(recurringRuleRepository, categoryRepository, recurringRuleMapper);
        lenient().when(recurringRuleMapper.toResponse(any(RecurringRule.class), any()))
                .thenAnswer(invocation -> {
                    RecurringRule rule = invocation.getArgument(0);
                    Category category = invocation.getArgument(1);
                    return new RecurringRuleResponse(rule.getId(), rule.getType(), rule.getAmount(),
                            rule.getCategoryId(), category != null ? category.getName() : null,
                            category != null ? category.getIcon() : null, rule.getDescription(), rule.getFrequency(),
                            rule.getStartDate(), rule.getEndDate(), rule.getNextDueDate(), rule.isActive());
                });
    }

    private Category expenseCategory() {
        Category category = new Category();
        category.setId(10L);
        category.setUserId(null);
        category.setName("Bills");
        category.setType("EXPENSE");
        category.setIcon("receipt");
        category.setSystem(true);
        return category;
    }

    private Category incomeCategory() {
        Category category = new Category();
        category.setId(11L);
        category.setUserId(null);
        category.setName("Salary");
        category.setType("INCOME");
        category.setIcon("briefcase");
        category.setSystem(true);
        return category;
    }

    private Category otherUsersCategory() {
        Category category = new Category();
        category.setId(30L);
        category.setUserId(OTHER_USER_ID);
        category.setName("Side Hustle");
        category.setType("EXPENSE");
        category.setIcon("laptop");
        category.setSystem(false);
        return category;
    }

    private RecurringRule ownRule() {
        RecurringRule rule = new RecurringRule();
        rule.setId(100L);
        rule.setUserId(USER_ID);
        rule.setCategoryId(10L);
        rule.setType("EXPENSE");
        rule.setAmount(new BigDecimal("50.00"));
        rule.setFrequency("MONTHLY");
        rule.setStartDate(LocalDate.of(2026, 6, 1));
        rule.setNextDueDate(LocalDate.of(2026, 8, 1));
        rule.setActive(true);
        return rule;
    }

    private RecurringRule othersRule() {
        RecurringRule rule = new RecurringRule();
        rule.setId(200L);
        rule.setUserId(OTHER_USER_ID);
        rule.setCategoryId(10L);
        rule.setType("EXPENSE");
        rule.setAmount(new BigDecimal("50.00"));
        rule.setFrequency("MONTHLY");
        rule.setStartDate(LocalDate.of(2026, 6, 1));
        rule.setNextDueDate(LocalDate.of(2026, 8, 1));
        rule.setActive(true);
        return rule;
    }

    // --- ownership ---

    @Test
    void getRuleHidesAnotherUsersRuleAs404() {
        when(recurringRuleRepository.findById(200L)).thenReturn(Optional.of(othersRule()));

        assertThatThrownBy(() -> recurringRuleService.getRule(USER_ID, 200L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRuleOnAnotherUsersRuleIsRejectedAs404() {
        when(recurringRuleRepository.findById(200L)).thenReturn(Optional.of(othersRule()));

        assertThatThrownBy(() -> recurringRuleService.updateRule(USER_ID, 200L,
                new RecurringRuleRequest("EXPENSE", new BigDecimal("50.00"), 10L, null, "MONTHLY",
                        LocalDate.of(2026, 6, 1), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRuleOnAnotherUsersRuleIsRejectedAs404() {
        when(recurringRuleRepository.findById(200L)).thenReturn(Optional.of(othersRule()));

        assertThatThrownBy(() -> recurringRuleService.deleteRule(USER_ID, 200L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(recurringRuleRepository, never()).delete(any(RecurringRule.class));
    }

    // --- category coherence ---

    @Test
    void createRuleRejectsAMismatchedCategoryType() {
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(incomeCategory()));

        assertThatThrownBy(() -> recurringRuleService.createRule(USER_ID,
                new RecurringRuleRequest("EXPENSE", new BigDecimal("50.00"), 11L, null, "MONTHLY",
                        LocalDate.of(2026, 6, 1), null)))
                .isInstanceOf(InvalidRecurringCategoryException.class);

        verify(recurringRuleRepository, never()).save(any());
    }

    @Test
    void createRuleRejectsAnotherUsersPrivateCategory() {
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(otherUsersCategory()));

        assertThatThrownBy(() -> recurringRuleService.createRule(USER_ID,
                new RecurringRuleRequest("EXPENSE", new BigDecimal("50.00"), 30L, null, "MONTHLY",
                        LocalDate.of(2026, 6, 1), null)))
                .isInstanceOf(InvalidRecurringCategoryException.class);
    }

    @Test
    void createRuleSavesForACoherentCategoryAndSeedsNextDueDateFromStartDate() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));
        when(recurringRuleRepository.save(any(RecurringRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringRuleResponse response = recurringRuleService.createRule(USER_ID,
                new RecurringRuleRequest("EXPENSE", new BigDecimal("50.00"), 10L, "Electricity", "MONTHLY",
                        LocalDate.of(2026, 6, 1), null));

        assertThat(response.categoryName()).isEqualTo("Bills");
        assertThat(response.nextDueDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.isActive()).isTrue();
    }

    // --- period validation ---

    @Test
    void createRuleRejectsAnEndDateBeforeTheStartDate() {
        assertThatThrownBy(() -> recurringRuleService.createRule(USER_ID,
                new RecurringRuleRequest("EXPENSE", new BigDecimal("50.00"), 10L, null, "MONTHLY",
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1))))
                .isInstanceOf(InvalidRecurringPeriodException.class);

        verify(recurringRuleRepository, never()).save(any());
    }

    // --- edit preserves future-only generation ---

    @Test
    void updateRulePreservesTheCurrentScheduleWhenStillAfterTheNewStartDate() {
        RecurringRule existing = ownRule(); // nextDueDate = 2026-08-01
        when(recurringRuleRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));

        recurringRuleService.updateRule(USER_ID, 100L,
                new RecurringRuleRequest("EXPENSE", new BigDecimal("75.00"), 10L, "Updated", "MONTHLY",
                        LocalDate.of(2026, 7, 1), null));

        assertThat(existing.getNextDueDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(existing.getAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void updateRulePullsTheScheduleForwardWhenTheNewStartDateIsLater() {
        RecurringRule existing = ownRule(); // nextDueDate = 2026-08-01
        when(recurringRuleRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));

        recurringRuleService.updateRule(USER_ID, 100L,
                new RecurringRuleRequest("EXPENSE", new BigDecimal("75.00"), 10L, null, "MONTHLY",
                        LocalDate.of(2026, 9, 1), null));

        assertThat(existing.getNextDueDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    // --- patch: pause/resume ---

    @Test
    void patchRuleTogglesIsActiveWithoutTouchingOtherFields() {
        RecurringRule existing = ownRule();
        when(recurringRuleRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));

        recurringRuleService.patchRule(USER_ID, 100L,
                new PatchRecurringRuleRequest(null, null, null, null, null, null, null, false));

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void patchRuleResumingAPausedRule() {
        RecurringRule existing = ownRule();
        existing.setActive(false);
        when(recurringRuleRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));

        recurringRuleService.patchRule(USER_ID, 100L,
                new PatchRecurringRuleRequest(null, null, null, null, null, null, null, true));

        assertThat(existing.isActive()).isTrue();
    }
}
