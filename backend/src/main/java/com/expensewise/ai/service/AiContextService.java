package com.expensewise.ai.service;

import com.expensewise.ai.dto.InsightResponse;
import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.CategoryBudgetLine;
import com.expensewise.budget.dto.OverallBudgetLine;
import com.expensewise.budget.service.BudgetService;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.dto.TransactionSummaryResponse;
import com.expensewise.transaction.service.TransactionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds two things from the SAME underlying month snapshot, reusing
 * TransactionService/BudgetService rather than querying repositories
 * directly (CLAUDE.md: "reuse their services ... do not duplicate query
 * logic"):
 *
 * <p>1. A compact text summary injected as system context on every chat
 * request (buildContextText) — contains ONLY the requesting userId's data,
 * since every call below is scoped to that userId.
 *
 * <p>2. Deterministic "Spending Analysis" insight cards (buildInsights) —
 * no model call, computed straight from budget over/under status. See
 * DECISIONS.md for why this is rule-based rather than AI-generated.
 */
@Service
public class AiContextService {

    private static final String EXPENSE = "EXPENSE";
    private static final int TOP_CATEGORIES_LIMIT = 3;
    private static final int INSIGHTS_LIMIT = 3;
    // Mirrors BudgetsView.vue's "approaching limit" threshold — kept as an
    // independently-declared constant rather than a shared library, same
    // as the rest of this project's frontend/backend convention split.
    private static final BigDecimal APPROACHING_THRESHOLD = BigDecimal.valueOf(80);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final TransactionService transactionService;
    private final BudgetService budgetService;

    public AiContextService(TransactionService transactionService, BudgetService budgetService) {
        this.transactionService = transactionService;
        this.budgetService = budgetService;
    }

    @Transactional(readOnly = true)
    public String buildContextText(Long userId) {
        Snapshot snapshot = buildSnapshot(userId);
        TransactionSummaryResponse summary = snapshot.summary();
        StringBuilder text = new StringBuilder();

        text.append("The user's financial snapshot for ").append(snapshot.periodMonth().format(MONTH_LABEL))
                .append(" (figures in MYR, this data belongs ONLY to the user you are talking to):\n");
        text.append("- Total income: RM ").append(summary.totalIncome()).append('\n');
        text.append("- Total expenses: RM ").append(summary.totalExpense()).append('\n');
        text.append("- Balance: RM ").append(summary.balance()).append('\n');

        if (!snapshot.topCategories().isEmpty()) {
            text.append("- Top spending categories: ");
            text.append(snapshot.topCategories().stream()
                    .map(c -> c.name() + " RM " + c.amount())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
            text.append('\n');
        }

        OverallBudgetLine overall = snapshot.budgetMonth().overall();
        if (overall.amount() != null) {
            text.append("- Overall budget: RM ").append(overall.amount()).append(" limit, RM ")
                    .append(overall.spent()).append(" spent, ").append(statusLabel(overall.exceeded(), overall.progressPercent()))
                    .append('\n');
        } else {
            text.append("- No overall budget set for this month.\n");
        }

        List<CategoryBudgetLine> budgetedCategories = snapshot.budgetMonth().categories().stream()
                .filter(c -> c.amount() != null)
                .toList();
        if (!budgetedCategories.isEmpty()) {
            text.append("- Category budgets: ");
            text.append(budgetedCategories.stream()
                    .map(c -> c.categoryName() + " RM " + c.spent() + "/RM " + c.amount()
                            + " (" + statusLabel(c.exceeded(), c.progressPercent()) + ")")
                    .reduce((a, b) -> a + "; " + b)
                    .orElse(""));
            text.append('\n');
        }

        return text.toString();
    }

    @Transactional(readOnly = true)
    public List<InsightResponse> buildInsights(Long userId) {
        Snapshot snapshot = buildSnapshot(userId);
        TransactionSummaryResponse summary = snapshot.summary();

        if (isEmptySnapshot(snapshot, summary)) {
            return List.of();
        }

        List<InsightResponse> critical = new ArrayList<>();
        List<InsightResponse> warning = new ArrayList<>();
        List<InsightResponse> positive = new ArrayList<>();

        addOverallBudgetInsight(snapshot.budgetMonth().overall(), critical, warning);
        addCategoryBudgetInsights(snapshot.budgetMonth().categories(), critical, warning);

        if (summary.balance().signum() > 0) {
            positive.add(new InsightResponse("Net savings this month",
                    "Income exceeded expenses by RM " + summary.balance() + " so far this month.",
                    "POSITIVE"));
        }

        List<InsightResponse> ordered = new ArrayList<>(critical.size() + warning.size() + positive.size());
        ordered.addAll(critical);
        ordered.addAll(warning);
        ordered.addAll(positive);

        if (ordered.isEmpty()) {
            // There IS budget/transaction data, just nothing over or close
            // to a limit — a reassuring note beats an empty panel.
            ordered.add(new InsightResponse("On track",
                    "No budgets are currently over or approaching their limit this month.",
                    "POSITIVE"));
        }

        return ordered.size() > INSIGHTS_LIMIT ? ordered.subList(0, INSIGHTS_LIMIT) : ordered;
    }

    private boolean isEmptySnapshot(Snapshot snapshot, TransactionSummaryResponse summary) {
        return summary.totalIncome().signum() == 0 && summary.totalExpense().signum() == 0
                && snapshot.budgetMonth().overall().amount() == null
                && snapshot.budgetMonth().categories().stream().allMatch(c -> c.amount() == null);
    }

    private void addOverallBudgetInsight(OverallBudgetLine overall, List<InsightResponse> critical,
                                          List<InsightResponse> warning) {
        if (overall.amount() == null) {
            return;
        }
        if (overall.exceeded()) {
            critical.add(new InsightResponse("Overall budget exceeded",
                    "You've spent RM " + overall.spent() + " against your RM " + overall.amount()
                            + " overall budget this month.",
                    "CRITICAL"));
        } else if (overall.progressPercent() != null && overall.progressPercent().compareTo(APPROACHING_THRESHOLD) >= 0) {
            warning.add(new InsightResponse("Approaching your overall budget",
                    "You've spent RM " + overall.spent() + " of your RM " + overall.amount()
                            + " overall budget (" + overall.progressPercent() + "%).",
                    "WARNING"));
        }
    }

    private void addCategoryBudgetInsights(List<CategoryBudgetLine> categories, List<InsightResponse> critical,
                                            List<InsightResponse> warning) {
        for (CategoryBudgetLine category : categories) {
            if (category.amount() == null) {
                continue;
            }
            if (category.exceeded()) {
                critical.add(new InsightResponse(category.categoryName() + " is over budget",
                        "You spent RM " + category.spent() + " against a RM " + category.amount()
                                + " limit this month.",
                        "CRITICAL"));
            } else if (category.progressPercent() != null && category.progressPercent().compareTo(APPROACHING_THRESHOLD) >= 0) {
                warning.add(new InsightResponse(category.categoryName() + " is approaching its limit",
                        "You spent RM " + category.spent() + " of a RM " + category.amount()
                                + " limit (" + category.progressPercent() + "%).",
                        "WARNING"));
            }
        }
    }

    private Snapshot buildSnapshot(Long userId) {
        BudgetMonthResponse budgetMonth = budgetService.getMonthBudgets(userId, null);
        LocalDate periodMonth = budgetMonth.periodMonth();
        LocalDate monthEnd = periodMonth.withDayOfMonth(periodMonth.lengthOfMonth());

        TransactionSummaryResponse summary = transactionService.getSummary(userId, null, null, periodMonth, monthEnd);

        // A page of 200 comfortably covers a month's expense rows for a
        // solo-user demo app (same "small dataset" assumption TransactionService
        // and BudgetService already rely on) without adding a dedicated
        // aggregate-by-category repository query.
        List<TransactionResponse> monthExpenses = transactionService.listTransactions(userId, EXPENSE, null,
                periodMonth, monthEnd, null, PageRequest.of(0, 200, Sort.by("transactionDate").descending())).getContent();

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (TransactionResponse transaction : monthExpenses) {
            byCategory.merge(transaction.categoryName(), transaction.amount(), BigDecimal::add);
        }
        List<CategorySpend> topCategories = byCategory.entrySet().stream()
                .map(e -> new CategorySpend(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategorySpend::amount).reversed())
                .limit(TOP_CATEGORIES_LIMIT)
                .toList();

        return new Snapshot(periodMonth, summary, topCategories, budgetMonth);
    }

    private String statusLabel(boolean exceeded, BigDecimal progressPercent) {
        if (exceeded) {
            return "over budget";
        }
        if (progressPercent != null && progressPercent.compareTo(APPROACHING_THRESHOLD) >= 0) {
            return "approaching limit";
        }
        return "on track";
    }

    private record CategorySpend(String name, BigDecimal amount) {
    }

    private record Snapshot(LocalDate periodMonth, TransactionSummaryResponse summary,
                             List<CategorySpend> topCategories, BudgetMonthResponse budgetMonth) {
    }
}
