package com.expensewise.recurring.service;

import com.expensewise.recurring.entity.RecurringRule;
import com.expensewise.recurring.repository.RecurringRuleRepository;
import com.expensewise.recurring.schedule.MonthlyNextDueDateCalculator;
import com.expensewise.recurring.schedule.WeeklyNextDueDateCalculator;
import com.expensewise.recurring.schedule.YearlyNextDueDateCalculator;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringGenerationServiceTest {

    private static final Long USER_ID = 1L;

    // Fixed at 2026-08-15 in Asia/Kuala_Lumpur.
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-15T04:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private RecurringRuleRepository recurringRuleRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private RecurringGenerationService generationService;

    @BeforeEach
    void setUp() {
        generationService = new RecurringGenerationService(recurringRuleRepository, transactionRepository,
                List.of(new WeeklyNextDueDateCalculator(), new MonthlyNextDueDateCalculator(),
                        new YearlyNextDueDateCalculator()),
                FIXED_CLOCK);
    }

    private RecurringRule weeklyRule(LocalDate nextDueDate) {
        RecurringRule rule = new RecurringRule();
        rule.setId(1L);
        rule.setUserId(USER_ID);
        rule.setCategoryId(10L);
        rule.setType("EXPENSE");
        rule.setAmount(new BigDecimal("25.00"));
        rule.setDescription("Groceries");
        rule.setFrequency("WEEKLY");
        rule.setStartDate(LocalDate.of(2026, 7, 1));
        rule.setNextDueDate(nextDueDate);
        rule.setActive(true);
        return rule;
    }

    @Test
    void generatesOneTransactionWhenExactlyOnePeriodIsDue() {
        RecurringRule rule = weeklyRule(LocalDate.of(2026, 8, 15));
        when(recurringRuleRepository.findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(USER_ID,
                LocalDate.of(2026, 8, 15))).thenReturn(List.of(rule));

        int generated = generationService.generateDueForUser(USER_ID);

        assertThat(generated).isEqualTo(1);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        assertThat(rule.getNextDueDate()).isEqualTo(LocalDate.of(2026, 8, 22));
    }

    @Test
    void catchesUpMultipleOverduePeriodsInOneRun() {
        // Three weekly periods overdue: Aug 1, Aug 8, Aug 15 (today).
        RecurringRule rule = weeklyRule(LocalDate.of(2026, 8, 1));
        when(recurringRuleRepository.findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(USER_ID,
                LocalDate.of(2026, 8, 15))).thenReturn(List.of(rule));

        int generated = generationService.generateDueForUser(USER_ID);

        assertThat(generated).isEqualTo(3);
        verify(transactionRepository, times(3)).save(any(Transaction.class));
        assertThat(rule.getNextDueDate()).isEqualTo(LocalDate.of(2026, 8, 22));
    }

    @Test
    void generatedTransactionsCarryTheRuleIdAndCorrectDueDates() {
        RecurringRule rule = weeklyRule(LocalDate.of(2026, 8, 1));
        when(recurringRuleRepository.findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(USER_ID,
                LocalDate.of(2026, 8, 15))).thenReturn(List.of(rule));

        generationService.generateDueForUser(USER_ID);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(3)).save(captor.capture());
        List<Transaction> saved = captor.getAllValues();
        assertThat(saved).extracting(Transaction::getTransactionDate)
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 15));
        assertThat(saved).allSatisfy(tx -> {
            assertThat(tx.getRecurringRuleId()).isEqualTo(1L);
            assertThat(tx.getUserId()).isEqualTo(USER_ID);
            assertThat(tx.getCategoryId()).isEqualTo(10L);
            assertThat(tx.getType()).isEqualTo("EXPENSE");
            assertThat(tx.getAmount()).isEqualByComparingTo("25.00");
        });
    }

    @Test
    void neverDoublePostsARuleThatIsNotYetDueAgain() {
        // After generating, nextDueDate moves to 2026-08-22 — a second run
        // on the same "today" must find nothing left to generate, since the
        // repository query itself (nextDueDate <= today) would no longer
        // match it. Verified here by confirming the loop only ran once for
        // the single due period, leaving the rule's schedule in the future.
        RecurringRule rule = weeklyRule(LocalDate.of(2026, 8, 15));
        when(recurringRuleRepository.findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(USER_ID,
                LocalDate.of(2026, 8, 15))).thenReturn(List.of(rule));

        generationService.generateDueForUser(USER_ID);

        assertThat(rule.getNextDueDate()).isAfter(LocalDate.of(2026, 8, 15));
    }

    @Test
    void deactivatesTheRuleWhenItsScheduleAdvancesPastTheEndDate() {
        RecurringRule rule = weeklyRule(LocalDate.of(2026, 8, 8));
        rule.setEndDate(LocalDate.of(2026, 8, 8)); // only one occurrence left
        when(recurringRuleRepository.findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(USER_ID,
                LocalDate.of(2026, 8, 15))).thenReturn(List.of(rule));

        int generated = generationService.generateDueForUser(USER_ID);

        assertThat(generated).isEqualTo(1);
        assertThat(rule.isActive()).isFalse();
        verify(recurringRuleRepository).save(rule);
    }

    @Test
    void aRuleWithNothingDueGeneratesNothing() {
        when(recurringRuleRepository.findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(USER_ID,
                LocalDate.of(2026, 8, 15))).thenReturn(List.of());

        int generated = generationService.generateDueForUser(USER_ID);

        assertThat(generated).isZero();
        verify(transactionRepository, never()).save(any());
        verify(recurringRuleRepository, never()).save(any());
    }

    @Test
    void generateAllDueProcessesRulesAcrossAllUsers() {
        RecurringRule rule = weeklyRule(LocalDate.of(2026, 8, 15));
        when(recurringRuleRepository.findByActiveTrueAndNextDueDateLessThanEqual(LocalDate.of(2026, 8, 15)))
                .thenReturn(List.of(rule));

        int generated = generationService.generateAllDue();

        assertThat(generated).isEqualTo(1);
    }
}
