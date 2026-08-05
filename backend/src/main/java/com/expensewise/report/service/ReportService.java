package com.expensewise.report.service;

import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.OverallBudgetLine;
import com.expensewise.budget.service.BudgetService;
import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.dashboard.dto.MonthlyFlowPoint;
import com.expensewise.exception.InvalidReportRequestException;
import com.expensewise.report.dto.BudgetSummaryResponse;
import com.expensewise.report.dto.CategoryBreakdownLine;
import com.expensewise.report.dto.ExportedReport;
import com.expensewise.report.dto.ReportResponse;
import com.expensewise.report.export.ReportExporter;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a period's report data model from existing modules — no new entity
 * or repository. Budget figures are always BudgetService's own numbers,
 * never recomputed; a report's transaction list and totals are derived from
 * the same raw Transaction rows the Dashboard/Budget modules already query,
 * scoped to the report's period instead of "this month". Monthly/yearly
 * period boundaries are anchored to Asia/Kuala_Lumpur, matching CLAUDE.md's
 * Time rule — a client only ever supplies a plain year/month integer, so
 * there's no client-supplied date to convert, but "the year 2026" and "the
 * month of March 2026" are themselves KL-calendar concepts here.
 */
@Service
public class ReportService {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String MONTHLY = "MONTHLY";
    private static final String YEARLY = "YEARLY";
    private static final String INCOME = "INCOME";
    private static final String EXPENSE = "EXPENSE";
    private static final int MIN_YEAR = 2000;
    // A generous single-page fetch, not a hard business limit — see
    // TransactionService.getSummary's "small dataset for a solo-user demo
    // app" precedent, which this reuses rather than paginating a report.
    private static final int MAX_REPORT_TRANSACTIONS = 5000;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final List<ReportExporter> exporters;
    private final Clock clock;

    public ReportService(TransactionRepository transactionRepository,
                          CategoryRepository categoryRepository,
                          TransactionService transactionService,
                          BudgetService budgetService,
                          List<ReportExporter> exporters,
                          Clock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.exporters = exporters;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportResponse buildReport(Long userId, String type, Integer year, Integer month) {
        String normalizedType = requireValidType(type);
        int resolvedYear = requireValidYear(year);

        LocalDate periodStart;
        LocalDate periodEnd;
        if (MONTHLY.equals(normalizedType)) {
            Month resolvedMonth = requireValidMonth(month);
            periodStart = LocalDate.of(resolvedYear, resolvedMonth, 1);
            periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        } else {
            periodStart = LocalDate.of(resolvedYear, Month.JANUARY, 1);
            periodEnd = LocalDate.of(resolvedYear, Month.DECEMBER, 31);
        }

        Specification<Transaction> spec = Specification.where(TransactionSpecifications.ownedBy(userId))
                .and(TransactionSpecifications.dateFrom(periodStart))
                .and(TransactionSpecifications.dateTo(periodEnd));
        List<Transaction> transactions = transactionRepository.findAll(spec);

        BigDecimal totalIncome = sum(transactions, INCOME);
        BigDecimal totalExpense = sum(transactions, EXPENSE);
        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        List<CategoryBreakdownLine> categoryBreakdown = buildCategoryBreakdown(transactions, totalExpense);
        List<TransactionResponse> transactionResponses = transactionService
                .listTransactions(userId, null, null, periodStart, periodEnd, null,
                        PageRequest.of(0, MAX_REPORT_TRANSACTIONS, Sort.by(Sort.Direction.ASC, "transactionDate")))
                .getContent();
        BudgetSummaryResponse budgetSummary = MONTHLY.equals(normalizedType)
                ? monthlyBudgetSummary(userId, periodStart)
                : yearlyBudgetSummary(userId, resolvedYear);

        // MONTHLY: the 6 consecutive months ending at the reported month
        // (same window Dashboard uses). YEARLY: the reported year's own 12
        // months — trendStart/trendEnd end up identical to periodStart/
        // periodEnd there, so this is one extra query, not a special case.
        LocalDate trendStart = MONTHLY.equals(normalizedType) ? periodStart.minusMonths(5) : periodStart;
        int trendMonths = MONTHLY.equals(normalizedType) ? 6 : 12;
        List<MonthlyFlowPoint> monthlyTrend = buildMonthlyTrend(userId, trendStart, periodEnd, trendMonths);

        return new ReportResponse(normalizedType, periodStart, periodEnd, totalIncome, totalExpense, netBalance,
                categoryBreakdown, transactionResponses, budgetSummary, monthlyTrend);
    }

    @Transactional(readOnly = true)
    public ExportedReport exportReport(Long userId, String type, Integer year, Integer month, String format) {
        ReportExporter exporter = exporters.stream()
                .filter(candidate -> candidate.format().equalsIgnoreCase(format))
                .findFirst()
                .orElseThrow(() -> new InvalidReportRequestException("format", "Unknown export format: " + format));

        ReportResponse report = buildReport(userId, type, year, month);
        byte[] content = exporter.export(report);
        String filename = "expensewise-report-" + report.periodStart() + "." + exporter.fileExtension();
        return new ExportedReport(content, filename, exporter.contentType());
    }

    private String requireValidType(String type) {
        if (type == null || (!type.equalsIgnoreCase(MONTHLY) && !type.equalsIgnoreCase(YEARLY))) {
            throw new InvalidReportRequestException("type", "type must be 'monthly' or 'yearly'");
        }
        return type.toUpperCase();
    }

    private int requireValidYear(Integer year) {
        int currentYear = Year.now(clock.withZone(KL_ZONE)).getValue();
        if (year == null || year < MIN_YEAR || year > currentYear) {
            throw new InvalidReportRequestException("year", "year must be between " + MIN_YEAR + " and " + currentYear);
        }
        return year;
    }

    private Month requireValidMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            throw new InvalidReportRequestException("month", "month must be between 1 and 12");
        }
        return Month.of(month);
    }

    private BigDecimal sum(List<Transaction> transactions, String type) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            if (type.equals(transaction.getType())) {
                total = total.add(transaction.getAmount());
            }
        }
        return total;
    }

    private List<CategoryBreakdownLine> buildCategoryBreakdown(List<Transaction> transactions, BigDecimal totalExpense) {
        Map<Long, BigDecimal> totalsByCategory = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (EXPENSE.equals(transaction.getType())) {
                totalsByCategory.merge(transaction.getCategoryId(), transaction.getAmount(), BigDecimal::add);
            }
        }
        if (totalsByCategory.isEmpty()) {
            return List.of();
        }

        Map<Long, Category> categoriesById = categoryRepository.findAllById(totalsByCategory.keySet()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return totalsByCategory.entrySet().stream()
                .map(entry -> {
                    Category category = categoriesById.get(entry.getKey());
                    BigDecimal amount = entry.getValue();
                    BigDecimal percentage = totalExpense.signum() == 0
                            ? BigDecimal.ZERO
                            : amount.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 0, RoundingMode.HALF_UP);
                    return new CategoryBreakdownLine(entry.getKey(),
                            category != null ? category.getName() : "Unknown",
                            category != null ? category.getIcon() : null,
                            amount, percentage);
                })
                .sorted(Comparator.comparing(CategoryBreakdownLine::amount).reversed())
                .toList();
    }

    /**
     * Buckets [trendStart, trendEnd] into `months` consecutive calendar
     * months starting at trendStart's month — same shape as
     * DashboardService.buildMonthlyTrend/AdminDashboardService.bucketByMonth,
     * generalized to an explicit start rather than "N months before the
     * current month", since a report's trend window is anchored to the
     * requested period, not to "now".
     */
    private List<MonthlyFlowPoint> buildMonthlyTrend(Long userId, LocalDate trendStart, LocalDate trendEnd, int months) {
        Specification<Transaction> spec = Specification.where(TransactionSpecifications.ownedBy(userId))
                .and(TransactionSpecifications.dateFrom(trendStart))
                .and(TransactionSpecifications.dateTo(trendEnd));
        List<Transaction> transactions = transactionRepository.findAll(spec);

        Map<LocalDate, BigDecimal[]> buckets = new LinkedHashMap<>();
        LocalDate firstBucket = trendStart.withDayOfMonth(1);
        for (int i = 0; i < months; i++) {
            buckets.put(firstBucket.plusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
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

    private BudgetSummaryResponse monthlyBudgetSummary(Long userId, LocalDate periodMonth) {
        OverallBudgetLine overall = budgetService.getMonthBudgets(userId, periodMonth).overall();
        return new BudgetSummaryResponse(overall.amount(), overall.spent(), overall.remaining(),
                overall.amount() != null);
    }

    /**
     * Budgets only ever exist per calendar month (budgets.period_month) —
     * there is no yearly budget row to read directly, so a yearly summary
     * sums each of the year's 12 monthly overall-budget lines. A month with
     * no overall budget set contributes 0 to totalBudgeted (never treated as
     * "unbounded"/ignored) but its spent figure — always computed regardless
     * of whether a limit exists — still counts toward totalSpent.
     */
    private BudgetSummaryResponse yearlyBudgetSummary(Long userId, int year) {
        BigDecimal totalBudgeted = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        boolean hasBudget = false;

        for (Month month : Month.values()) {
            BudgetMonthResponse monthBudgets = budgetService.getMonthBudgets(userId, LocalDate.of(year, month, 1));
            OverallBudgetLine overall = monthBudgets.overall();
            totalSpent = totalSpent.add(overall.spent());
            if (overall.amount() != null) {
                totalBudgeted = totalBudgeted.add(overall.amount());
                hasBudget = true;
            }
        }

        BigDecimal totalRemaining = hasBudget ? totalBudgeted.subtract(totalSpent) : null;
        return new BudgetSummaryResponse(hasBudget ? totalBudgeted : null, totalSpent, totalRemaining, hasBudget);
    }
}
