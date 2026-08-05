package com.expensewise.dashboard.service;

import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.service.BudgetService;
import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.dashboard.dto.CategoryAmountResponse;
import com.expensewise.dashboard.dto.DashboardResponse;
import com.expensewise.dashboard.dto.DashboardSummaryResponse;
import com.expensewise.dashboard.dto.MonthlyFlowPoint;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import com.expensewise.transaction.repository.spec.TransactionSpecifications;
import com.expensewise.transaction.service.TransactionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates the personal, USER-only home screen from existing modules —
 * no new entity or repository of its own. Budget progress is entirely
 * delegated to BudgetService and recent transactions to TransactionService;
 * this class only computes the figures neither of those already expose
 * (this-month/overall totals, the monthly income/expense series, and the
 * current month's expense-by-category breakdown).
 */
@Service
public class DashboardService {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String INCOME = "INCOME";
    private static final String EXPENSE = "EXPENSE";

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final Clock clock;

    public DashboardService(TransactionRepository transactionRepository,
                             CategoryRepository categoryRepository,
                             TransactionService transactionService,
                             BudgetService budgetService,
                             Clock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, int months, int recentLimit) {
        // Transaction volumes are small for a solo-user demo app (see
        // TransactionService.getSummary/BudgetService.sumExpenses) — one
        // unfiltered fetch, then every figure below is derived from it in
        // Java, rather than one query per widget.
        Specification<Transaction> ownedSpec = Specification.where(TransactionSpecifications.ownedBy(userId));
        List<Transaction> transactions = transactionRepository.findAll(ownedSpec);

        LocalDate currentMonth = LocalDate.now(clock.withZone(KL_ZONE)).withDayOfMonth(1);

        DashboardSummaryResponse summary = buildSummary(transactions, currentMonth);
        List<MonthlyFlowPoint> monthlyTrend = buildMonthlyTrend(transactions, currentMonth, months);
        List<CategoryAmountResponse> expenseByCategory = buildExpenseByCategory(transactions, currentMonth);
        BudgetMonthResponse budgetUtilisation = budgetService.getMonthBudgets(userId, currentMonth);
        List<TransactionResponse> recentTransactions = transactionService
                .listTransactions(userId, null, null, null, null, null,
                        PageRequest.of(0, recentLimit, Sort.by(Sort.Direction.DESC, "transactionDate")))
                .getContent();

        return new DashboardResponse(summary, monthlyTrend, expenseByCategory, budgetUtilisation, recentTransactions);
    }

    private DashboardSummaryResponse buildSummary(List<Transaction> transactions, LocalDate currentMonth) {
        LocalDate monthEnd = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth());

        BigDecimal thisMonthIncome = BigDecimal.ZERO;
        BigDecimal thisMonthExpense = BigDecimal.ZERO;
        BigDecimal overallIncome = BigDecimal.ZERO;
        BigDecimal overallExpense = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            boolean isIncome = INCOME.equals(transaction.getType());
            if (isIncome) {
                overallIncome = overallIncome.add(transaction.getAmount());
            } else {
                overallExpense = overallExpense.add(transaction.getAmount());
            }

            LocalDate date = transaction.getTransactionDate();
            boolean inCurrentMonth = !date.isBefore(currentMonth) && !date.isAfter(monthEnd);
            if (inCurrentMonth) {
                if (isIncome) {
                    thisMonthIncome = thisMonthIncome.add(transaction.getAmount());
                } else {
                    thisMonthExpense = thisMonthExpense.add(transaction.getAmount());
                }
            }
        }

        BigDecimal thisMonthBalance = thisMonthIncome.subtract(thisMonthExpense);
        BigDecimal overallBalance = overallIncome.subtract(overallExpense);
        return new DashboardSummaryResponse(thisMonthIncome, thisMonthExpense, thisMonthBalance, overallBalance);
    }

    /**
     * Builds exactly `months` consecutive month buckets ending at the
     * current Asia/Kuala_Lumpur month (oldest first) — same shape as
     * AdminDashboardService.bucketByMonth, but summing BigDecimal income/
     * expense instead of counting rows, and bucketing directly off
     * transaction_date (already a plain DATE, no Instant/timezone
     * conversion needed) instead of an Instant createdAt column.
     */
    private List<MonthlyFlowPoint> buildMonthlyTrend(List<Transaction> transactions, LocalDate currentMonth, int months) {
        Map<LocalDate, BigDecimal[]> buckets = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            buckets.put(currentMonth.minusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (Transaction transaction : transactions) {
            LocalDate bucket = transaction.getTransactionDate().withDayOfMonth(1);
            BigDecimal[] point = buckets.get(bucket);
            if (point == null) {
                continue;
            }
            if (INCOME.equals(transaction.getType())) {
                point[0] = point[0].add(transaction.getAmount());
            } else {
                point[1] = point[1].add(transaction.getAmount());
            }
        }

        List<MonthlyFlowPoint> points = new ArrayList<>(buckets.size());
        buckets.forEach((month, point) -> points.add(new MonthlyFlowPoint(month, point[0], point[1])));
        return points;
    }

    private List<CategoryAmountResponse> buildExpenseByCategory(List<Transaction> transactions, LocalDate currentMonth) {
        LocalDate monthEnd = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth());

        Map<Long, BigDecimal> totalsByCategory = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (!EXPENSE.equals(transaction.getType())) {
                continue;
            }
            LocalDate date = transaction.getTransactionDate();
            if (date.isBefore(currentMonth) || date.isAfter(monthEnd)) {
                continue;
            }
            totalsByCategory.merge(transaction.getCategoryId(), transaction.getAmount(), BigDecimal::add);
        }

        if (totalsByCategory.isEmpty()) {
            return List.of();
        }

        Map<Long, Category> categoriesById = categoryRepository.findAllById(totalsByCategory.keySet()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return totalsByCategory.entrySet().stream()
                .map(entry -> {
                    Category category = categoriesById.get(entry.getKey());
                    return new CategoryAmountResponse(
                            entry.getKey(),
                            category != null ? category.getName() : "Unknown",
                            category != null ? category.getIcon() : null,
                            entry.getValue());
                })
                .sorted(Comparator.comparing(CategoryAmountResponse::amount).reversed())
                .toList();
    }
}
