package com.expensewise.report.service;

import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.OverallBudgetLine;
import com.expensewise.budget.service.BudgetService;
import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.exception.InvalidReportRequestException;
import com.expensewise.report.dto.CategoryBreakdownLine;
import com.expensewise.report.dto.ExportedReport;
import com.expensewise.report.dto.ReportResponse;
import com.expensewise.report.export.ReportExporter;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import com.expensewise.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOOD_CATEGORY_ID = 10L;
    private static final Long SALARY_CATEGORY_ID = 12L;

    /** Fixed at 2026-07-15 12:00 UTC — same calendar day/month in KL (UTC+8), so "now" is safely mid-2026. */
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private ReportExporter pdfExporter;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        lenient().when(pdfExporter.format()).thenReturn("pdf");
        reportService = new ReportService(transactionRepository, categoryRepository, transactionService,
                budgetService, List.of(pdfExporter), FIXED_CLOCK);
        reportService.setSelf(reportService);
    }

    private Transaction transaction(String type, String amount, LocalDate date, Long categoryId) {
        Transaction transaction = new Transaction();
        transaction.setUserId(USER_ID);
        transaction.setType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionDate(date);
        transaction.setCategoryId(categoryId);
        return transaction;
    }

    private void stubTransactions(List<Transaction> transactions) {
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);
    }

    private void stubTransactionServiceEmpty() {
        lenient().when(transactionService.listTransactions(eq(USER_ID), isNull(), isNull(), any(LocalDate.class),
                any(LocalDate.class), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    private void stubNoBudget() {
        lenient().when(budgetService.getMonthBudgets(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(new BudgetMonthResponse(LocalDate.of(2026, Month.JULY, 1),
                        new OverallBudgetLine(null, null, BigDecimal.ZERO, null, null, false), List.of()));
    }

    // --- monthly aggregation ---

    @Test
    void monthlyReportComputesTotalsScopedToTheGivenMonth() {
        stubTransactions(List.of(
                transaction("INCOME", "3000.00", LocalDate.of(2026, Month.MARCH, 5), SALARY_CATEGORY_ID),
                transaction("EXPENSE", "80.00", LocalDate.of(2026, Month.MARCH, 10), FOOD_CATEGORY_ID)));
        stubTransactionServiceEmpty();
        stubNoBudget();

        ReportResponse report = reportService.buildReport(USER_ID, "monthly", 2026, 3);

        assertThat(report.type()).isEqualTo("MONTHLY");
        assertThat(report.periodStart()).isEqualTo(LocalDate.of(2026, Month.MARCH, 1));
        assertThat(report.periodEnd()).isEqualTo(LocalDate.of(2026, Month.MARCH, 31));
        assertThat(report.totalIncome()).isEqualByComparingTo("3000.00");
        assertThat(report.totalExpense()).isEqualByComparingTo("80.00");
        assertThat(report.netBalance()).isEqualByComparingTo("2920.00");
    }

    @Test
    void monthlyReportOnlyIncludesTransactionsInsideTheRequestedMonth() {
        stubTransactions(List.of(
                transaction("EXPENSE", "50.00", LocalDate.of(2026, Month.MARCH, 1), FOOD_CATEGORY_ID),
                transaction("EXPENSE", "60.00", LocalDate.of(2026, Month.MARCH, 31), FOOD_CATEGORY_ID)));
        stubTransactionServiceEmpty();
        stubNoBudget();

        ReportResponse report = reportService.buildReport(USER_ID, "monthly", 2026, 3);

        // The repository call itself is scoped via TransactionSpecifications
        // dateFrom/dateTo (mocked here to return exactly this list), so this
        // assertion is really checking the sum, not the filtering — the KL
        // month-boundary computation (periodStart/periodEnd) is what matters.
        assertThat(report.totalExpense()).isEqualByComparingTo("110.00");
    }

    @Test
    void monthlyReportTrendCoversTheSixMonthsEndingAtTheReportedMonth() {
        stubTransactions(List.of(
                transaction("INCOME", "1000.00", LocalDate.of(2025, Month.OCTOBER, 5), SALARY_CATEGORY_ID),
                transaction("EXPENSE", "80.00", LocalDate.of(2026, Month.MARCH, 10), FOOD_CATEGORY_ID)));
        stubTransactionServiceEmpty();
        stubNoBudget();

        ReportResponse report = reportService.buildReport(USER_ID, "monthly", 2026, 3);

        assertThat(report.monthlyTrend()).hasSize(6);
        assertThat(report.monthlyTrend().get(0).month()).isEqualTo(LocalDate.of(2025, Month.OCTOBER, 1));
        assertThat(report.monthlyTrend().get(0).income()).isEqualByComparingTo("1000.00");
        assertThat(report.monthlyTrend().get(5).month()).isEqualTo(LocalDate.of(2026, Month.MARCH, 1));
        assertThat(report.monthlyTrend().get(5).expense()).isEqualByComparingTo("80.00");
    }

    // --- yearly aggregation ---

    @Test
    void yearlyReportSpansJanuaryThroughDecember() {
        stubTransactions(List.of(
                transaction("INCOME", "1000.00", LocalDate.of(2025, Month.JANUARY, 1), SALARY_CATEGORY_ID),
                transaction("EXPENSE", "500.00", LocalDate.of(2025, Month.DECEMBER, 31), FOOD_CATEGORY_ID)));
        stubTransactionServiceEmpty();
        stubNoBudget();

        ReportResponse report = reportService.buildReport(USER_ID, "yearly", 2025, null);

        assertThat(report.type()).isEqualTo("YEARLY");
        assertThat(report.periodStart()).isEqualTo(LocalDate.of(2025, Month.JANUARY, 1));
        assertThat(report.periodEnd()).isEqualTo(LocalDate.of(2025, Month.DECEMBER, 31));
        assertThat(report.totalIncome()).isEqualByComparingTo("1000.00");
        assertThat(report.totalExpense()).isEqualByComparingTo("500.00");
    }

    @Test
    void yearlyReportTrendCoversAllTwelveMonthsOfTheYear() {
        stubTransactions(List.of(
                transaction("INCOME", "500.00", LocalDate.of(2026, Month.JANUARY, 5), SALARY_CATEGORY_ID),
                transaction("EXPENSE", "200.00", LocalDate.of(2026, Month.DECEMBER, 20), FOOD_CATEGORY_ID)));
        stubTransactionServiceEmpty();
        stubNoBudget();

        ReportResponse report = reportService.buildReport(USER_ID, "yearly", 2026, null);

        assertThat(report.monthlyTrend()).hasSize(12);
        assertThat(report.monthlyTrend().get(0).month()).isEqualTo(LocalDate.of(2026, Month.JANUARY, 1));
        assertThat(report.monthlyTrend().get(0).income()).isEqualByComparingTo("500.00");
        assertThat(report.monthlyTrend().get(11).month()).isEqualTo(LocalDate.of(2026, Month.DECEMBER, 1));
        assertThat(report.monthlyTrend().get(11).expense()).isEqualByComparingTo("200.00");
    }

    @Test
    void yearlyBudgetSummarySumsEveryMonthsOverallBudget() {
        stubTransactions(List.of());
        stubTransactionServiceEmpty();

        // January has a 500.00 overall budget with 100.00 spent; every other
        // month has none set (amount null) but still reports its own spent.
        OverallBudgetLine january = new OverallBudgetLine(1L, new BigDecimal("500.00"), new BigDecimal("100.00"),
                new BigDecimal("400.00"), new BigDecimal("20"), false);
        OverallBudgetLine noBudget = new OverallBudgetLine(null, null, new BigDecimal("30.00"), null, null, false);
        when(budgetService.getMonthBudgets(USER_ID, LocalDate.of(2026, Month.JANUARY, 1)))
                .thenReturn(new BudgetMonthResponse(LocalDate.of(2026, Month.JANUARY, 1), january, List.of()));
        for (Month month : Month.values()) {
            if (!month.equals(Month.JANUARY)) {
                lenient().when(budgetService.getMonthBudgets(USER_ID, LocalDate.of(2026, month, 1)))
                        .thenReturn(new BudgetMonthResponse(LocalDate.of(2026, month, 1), noBudget, List.of()));
            }
        }

        ReportResponse report = reportService.buildReport(USER_ID, "yearly", 2026, null);

        assertThat(report.budgetSummary().hasBudget()).isTrue();
        assertThat(report.budgetSummary().totalBudgeted()).isEqualByComparingTo("500.00");
        // 100.00 (January) + 30.00 * 11 other months.
        assertThat(report.budgetSummary().totalSpent()).isEqualByComparingTo("430.00");
        assertThat(report.budgetSummary().totalRemaining()).isEqualByComparingTo("70.00");
    }

    @Test
    void yearlyBudgetSummaryHasNoBudgetWhenNoMonthEverHadOneSet() {
        stubTransactions(List.of());
        stubTransactionServiceEmpty();
        OverallBudgetLine noBudget = new OverallBudgetLine(null, null, BigDecimal.ZERO, null, null, false);
        lenient().when(budgetService.getMonthBudgets(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(new BudgetMonthResponse(LocalDate.of(2026, Month.JANUARY, 1), noBudget, List.of()));

        ReportResponse report = reportService.buildReport(USER_ID, "yearly", 2026, null);

        assertThat(report.budgetSummary().hasBudget()).isFalse();
        assertThat(report.budgetSummary().totalBudgeted()).isNull();
        assertThat(report.budgetSummary().totalRemaining()).isNull();
    }

    // --- category breakdown ---

    @Test
    void categoryBreakdownOnlyIncludesExpensesAndComputesPercentageShare() {
        stubTransactions(List.of(
                transaction("EXPENSE", "75.00", LocalDate.of(2026, Month.MARCH, 5), FOOD_CATEGORY_ID),
                transaction("EXPENSE", "25.00", LocalDate.of(2026, Month.MARCH, 6), FOOD_CATEGORY_ID),
                transaction("INCOME", "500.00", LocalDate.of(2026, Month.MARCH, 1), SALARY_CATEGORY_ID)));
        stubTransactionServiceEmpty();
        stubNoBudget();

        Category food = new Category();
        food.setId(FOOD_CATEGORY_ID);
        food.setName("Food");
        food.setIcon("utensils");
        when(categoryRepository.findAllById(any())).thenReturn(List.of(food));

        ReportResponse report = reportService.buildReport(USER_ID, "monthly", 2026, 3);

        assertThat(report.categoryBreakdown()).hasSize(1);
        CategoryBreakdownLine line = report.categoryBreakdown().get(0);
        assertThat(line.categoryName()).isEqualTo("Food");
        assertThat(line.amount()).isEqualByComparingTo("100.00");
        assertThat(line.percentage()).isEqualByComparingTo("100");
    }

    // --- reuse, not recompute ---

    @Test
    void transactionListReusesTransactionServiceScopedToThePeriod() {
        stubTransactions(List.of());
        stubNoBudget();
        List<TransactionResponse> expected = List.of();
        when(transactionService.listTransactions(eq(USER_ID), isNull(), isNull(),
                eq(LocalDate.of(2026, Month.MARCH, 1)), eq(LocalDate.of(2026, Month.MARCH, 31)), isNull(),
                any(Pageable.class))).thenReturn(new PageImpl<>(expected));

        ReportResponse report = reportService.buildReport(USER_ID, "monthly", 2026, 3);

        assertThat(report.transactions()).isEqualTo(expected);
    }

    @Test
    void monthlyBudgetSummaryReusesBudgetServiceRatherThanRecomputingProgress() {
        stubTransactions(List.of());
        stubTransactionServiceEmpty();
        OverallBudgetLine overall = new OverallBudgetLine(1L, new BigDecimal("500.00"), new BigDecimal("120.00"),
                new BigDecimal("380.00"), new BigDecimal("24"), false);
        when(budgetService.getMonthBudgets(USER_ID, LocalDate.of(2026, Month.MARCH, 1)))
                .thenReturn(new BudgetMonthResponse(LocalDate.of(2026, Month.MARCH, 1), overall, List.of()));

        ReportResponse report = reportService.buildReport(USER_ID, "monthly", 2026, 3);

        assertThat(report.budgetSummary().totalBudgeted()).isEqualByComparingTo("500.00");
        assertThat(report.budgetSummary().totalSpent()).isEqualByComparingTo("120.00");
        assertThat(report.budgetSummary().totalRemaining()).isEqualByComparingTo("380.00");
        verify(budgetService).getMonthBudgets(USER_ID, LocalDate.of(2026, Month.MARCH, 1));
    }

    // --- validation ---

    @Test
    void rejectsAnUnknownReportType() {
        assertThatThrownBy(() -> reportService.buildReport(USER_ID, "weekly", 2026, 3))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void rejectsAMonthlyReportWithNoMonth() {
        assertThatThrownBy(() -> reportService.buildReport(USER_ID, "monthly", 2026, null))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void rejectsAMonthOutOfRange() {
        assertThatThrownBy(() -> reportService.buildReport(USER_ID, "monthly", 2026, 13))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void rejectsAYearInTheFuture() {
        assertThatThrownBy(() -> reportService.buildReport(USER_ID, "yearly", 2027, null))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    // --- export ---

    @Test
    void exportReportDelegatesToTheMatchingExporterByFormat() {
        stubTransactions(List.of());
        stubTransactionServiceEmpty();
        stubNoBudget();
        byte[] fakePdfBytes = {1, 2, 3};
        when(pdfExporter.export(any(ReportResponse.class))).thenReturn(fakePdfBytes);
        when(pdfExporter.contentType()).thenReturn("application/pdf");
        when(pdfExporter.fileExtension()).thenReturn("pdf");

        ExportedReport exported = reportService.exportReport(USER_ID, "monthly", 2026, 3, "PDF");

        assertThat(exported.content()).isEqualTo(fakePdfBytes);
        assertThat(exported.contentType()).isEqualTo("application/pdf");
        assertThat(exported.filename()).isEqualTo("expensewise-report-2026-03-01.pdf");
    }

    @Test
    void exportReportRejectsAnUnknownFormat() {
        assertThatThrownBy(() -> reportService.exportReport(USER_ID, "monthly", 2026, 3, "csv"))
                .isInstanceOf(InvalidReportRequestException.class);
    }
}
