package com.expensewise.recurring.service;

import com.expensewise.recurring.entity.RecurringRule;
import com.expensewise.recurring.repository.RecurringRuleRepository;
import com.expensewise.recurring.schedule.NextDueDateCalculator;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Model A (auto-post): generation happens on the due date with no user
 * confirmation step. Contains the catch-up loop so it's unit-testable
 * independent of RecurringGenerationScheduler (the @Scheduled entry point)
 * and of the manual "generate due now" controller endpoint — both simply
 * call generateAllDue()/generateDueForUser(userId).
 */
@Service
public class RecurringGenerationService {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    private final RecurringRuleRepository recurringRuleRepository;
    private final TransactionRepository transactionRepository;
    private final Map<String, NextDueDateCalculator> calculatorsByFrequency;
    private final Clock clock;

    public RecurringGenerationService(RecurringRuleRepository recurringRuleRepository,
                                       TransactionRepository transactionRepository,
                                       List<NextDueDateCalculator> calculators,
                                       Clock clock) {
        this.recurringRuleRepository = recurringRuleRepository;
        this.transactionRepository = transactionRepository;
        this.calculatorsByFrequency = calculators.stream()
                .collect(Collectors.toMap(NextDueDateCalculator::frequency, Function.identity()));
        this.clock = clock;
    }

    /** Processes every active, due rule across all users — the scheduled job's entry point. */
    @Transactional
    public int generateAllDue() {
        LocalDate today = today();
        List<RecurringRule> dueRules = recurringRuleRepository.findByActiveTrueAndNextDueDateLessThanEqual(today);
        return generate(dueRules, today);
    }

    /** Processes only the given user's due rules — the manual "generate due now" demo endpoint. */
    @Transactional
    public int generateDueForUser(Long userId) {
        LocalDate today = today();
        List<RecurringRule> dueRules =
                recurringRuleRepository.findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(userId, today);
        return generate(dueRules, today);
    }

    private int generate(List<RecurringRule> dueRules, LocalDate today) {
        int totalGenerated = 0;
        for (RecurringRule rule : dueRules) {
            totalGenerated += generateForRule(rule, today);
        }
        return totalGenerated;
    }

    /**
     * Catch-up loop: while the rule is due (nextDueDate <= today) and not
     * past its end date, create a transaction for that occurrence, then
     * advance nextDueDate. Advancing AFTER creating guarantees each
     * occurrence is generated exactly once, even if this method is called
     * again before the next real due date (idempotent — never double-posts).
     * If the advanced date lands after the end date, the rule is
     * deactivated.
     */
    private int generateForRule(RecurringRule rule, LocalDate today) {
        NextDueDateCalculator calculator = calculatorsByFrequency.get(rule.getFrequency());
        int generated = 0;

        while (!rule.getNextDueDate().isAfter(today)
                && (rule.getEndDate() == null || !rule.getNextDueDate().isAfter(rule.getEndDate()))) {
            Transaction transaction = new Transaction();
            transaction.setUserId(rule.getUserId());
            transaction.setCategoryId(rule.getCategoryId());
            transaction.setRecurringRuleId(rule.getId());
            transaction.setType(rule.getType());
            transaction.setAmount(rule.getAmount());
            transaction.setTransactionDate(rule.getNextDueDate());
            transaction.setDescription(rule.getDescription());
            transactionRepository.save(transaction);

            rule.setNextDueDate(calculator.nextDueDate(rule.getNextDueDate(), rule.getStartDate()));
            generated++;
        }

        if (rule.getEndDate() != null && rule.getNextDueDate().isAfter(rule.getEndDate())) {
            rule.setActive(false);
        }
        if (generated > 0) {
            recurringRuleRepository.save(rule);
        }
        return generated;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KL_ZONE));
    }
}
