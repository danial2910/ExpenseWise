package com.expensewise.recurring.scheduler;

import com.expensewise.recurring.service.RecurringGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs daily, shortly after midnight in Asia/Kuala_Lumpur (CLAUDE.md's Time
 * rule — due dates are anchored to KL, not UTC). Delegates all logic to
 * RecurringGenerationService so this class stays a thin trigger, keeping
 * the generation logic itself testable without the scheduler.
 */
@Component
public class RecurringGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringGenerationScheduler.class);

    private final RecurringGenerationService recurringGenerationService;

    public RecurringGenerationScheduler(RecurringGenerationService recurringGenerationService) {
        this.recurringGenerationService = recurringGenerationService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kuala_Lumpur")
    public void generateDueTransactions() {
        int generated = recurringGenerationService.generateAllDue();
        log.info("Recurring generation run created {} transaction(s)", generated);
    }
}
