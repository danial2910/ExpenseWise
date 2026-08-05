package com.expensewise.dashboard.service;

import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.OverallBudgetLine;
import com.expensewise.budget.service.BudgetService;
import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.dashboard.dto.CategoryAmountResponse;
import com.expensewise.dashboard.dto.DashboardResponse;
import com.expensewise.dashboard.dto.MonthlyFlowPoint;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import com.expensewise.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOOD_CATEGORY_ID = 10L;
    private static final Long TRANSPORT_CATEGORY_ID = 11L;
    private static final Long SALARY_CATEGORY_ID = 12L;

    /** Fixed at 2026-07-15 12:00 UTC — same calendar day/month in KL (UTC+8). */
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private BudgetService budgetService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(transactionRepository, categoryRepository, transactionService,
                budgetService, FIXED_CLOCK);
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

    private void stubDefaults(List<Transaction> transactions) {
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);
        // lenient: a couple of tests re-stub these with a more specific
        // return value to assert the exact pass-through, which would
        // otherwise make this default stub "unnecessary" under strict stubs.
        lenient().when(budgetService.getMonthBudgets(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(new BudgetMonthResponse(LocalDate.of(2026, Month.JULY, 1),
                        new OverallBudgetLine(null, null, BigDecimal.ZERO, null, null, false), List.of()));
        lenient().when(transactionService.listTransactions(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    // --- summary totals ---

    @Test
    void summaryComputesThisMonthAndOverallTotalsWithBigDecimal() {
        List<Transaction> transactions = List.of(
                transaction("INCOME", "2000.00", LocalDate.of(2026, Month.JULY, 5), SALARY_CATEGORY_ID),
                transaction("EXPENSE", "300.00", LocalDate.of(2026, Month.JULY, 10), FOOD_CATEGORY_ID),
                // Outside the current month — must count toward overallBalance only.
                transaction("EXPENSE", "150.00", LocalDate.of(2026, Month.JUNE, 20), FOOD_CATEGORY_ID));
        stubDefaults(transactions);

        DashboardResponse response = dashboardService.getDashboard(USER_ID, 6, 5);

        assertThat(response.summary().thisMonthIncome()).isEqualByComparingTo("2000.00");
        assertThat(response.summary().thisMonthExpense()).isEqualByComparingTo("300.00");
        assertThat(response.summary().thisMonthBalance()).isEqualByComparingTo("1700.00");
        assertThat(response.summary().overallBalance()).isEqualByComparingTo("1550.00");
    }

    // --- monthly trend / KL month boundary ---

    @Test
    void monthlyTrendBucketsByCalendarMonthOverTheRequestedWindow() {
        List<Transaction> transactions = List.of(
                transaction("INCOME", "1000.00", LocalDate.of(2026, Month.JUNE, 5), SALARY_CATEGORY_ID),
                transaction("EXPENSE", "200.00", LocalDate.of(2026, Month.JUNE, 6), FOOD_CATEGORY_ID),
                transaction("INCOME", "1200.00", LocalDate.of(2026, Month.JULY, 5), SALARY_CATEGORY_ID),
                transaction("EXPENSE", "400.00", LocalDate.of(2026, Month.JULY, 6), FOOD_CATEGORY_ID));
        stubDefaults(transactions);

        DashboardResponse response = dashboardService.getDashboard(USER_ID, 2, 5);

        List<MonthlyFlowPoint> trend = response.monthlyTrend();
        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).month()).isEqualTo(LocalDate.of(2026, Month.JUNE, 1));
        assertThat(trend.get(0).income()).isEqualByComparingTo("1000.00");
        assertThat(trend.get(0).expense()).isEqualByComparingTo("200.00");
        assertThat(trend.get(1).month()).isEqualTo(LocalDate.of(2026, Month.JULY, 1));
        assertThat(trend.get(1).income()).isEqualByComparingTo("1200.00");
        assertThat(trend.get(1).expense()).isEqualByComparingTo("400.00");
    }

    @Test
    void monthlyTrendAnchorsTheCurrentMonthInAsiaKualaLumpurNotUtc() {
        // 2026-07-31 23:00 UTC is already 2026-08-01 07:00 in Asia/Kuala_Lumpur
        // (UTC+8) — the last bucket must be August, not July.
        Clock lateJulyUtc = Clock.fixed(Instant.parse("2026-07-31T23:00:00Z"), ZoneId.of("UTC"));
        DashboardService klService = new DashboardService(transactionRepository, categoryRepository,
                transactionService, budgetService, lateJulyUtc);
        stubDefaults(List.of());

        DashboardResponse response = klService.getDashboard(USER_ID, 1, 5);

        assertThat(response.monthlyTrend()).hasSize(1);
        assertThat(response.monthlyTrend().get(0).month()).isEqualTo(LocalDate.of(2026, Month.AUGUST, 1));
    }

    // --- expense by category ---

    @Test
    void expenseByCategoryOnlySumsCurrentMonthExpensesPerCategory() {
        List<Transaction> transactions = List.of(
                transaction("EXPENSE", "60.00", LocalDate.of(2026, Month.JULY, 3), FOOD_CATEGORY_ID),
                transaction("EXPENSE", "40.00", LocalDate.of(2026, Month.JULY, 20), FOOD_CATEGORY_ID),
                transaction("EXPENSE", "25.00", LocalDate.of(2026, Month.JULY, 12), TRANSPORT_CATEGORY_ID),
                // Income must never appear in the expense breakdown.
                transaction("INCOME", "2000.00", LocalDate.of(2026, Month.JULY, 1), SALARY_CATEGORY_ID),
                // A prior month's expense must not leak into this month's breakdown.
                transaction("EXPENSE", "999.00", LocalDate.of(2026, Month.JUNE, 15), FOOD_CATEGORY_ID));
        stubDefaults(transactions);

        Category food = new Category();
        food.setId(FOOD_CATEGORY_ID);
        food.setName("Food");
        food.setIcon("utensils");
        Category transport = new Category();
        transport.setId(TRANSPORT_CATEGORY_ID);
        transport.setName("Transport");
        transport.setIcon("car");
        when(categoryRepository.findAllById(any())).thenReturn(List.of(food, transport));

        DashboardResponse response = dashboardService.getDashboard(USER_ID, 6, 5);

        List<CategoryAmountResponse> breakdown = response.expenseByCategory();
        assertThat(breakdown).hasSize(2);
        CategoryAmountResponse foodLine = breakdown.stream()
                .filter(c -> c.categoryId().equals(FOOD_CATEGORY_ID)).findFirst().orElseThrow();
        assertThat(foodLine.amount()).isEqualByComparingTo("100.00");
        assertThat(foodLine.categoryName()).isEqualTo("Food");
        CategoryAmountResponse transportLine = breakdown.stream()
                .filter(c -> c.categoryId().equals(TRANSPORT_CATEGORY_ID)).findFirst().orElseThrow();
        assertThat(transportLine.amount()).isEqualByComparingTo("25.00");
    }

    // --- reuse, not recompute ---

    @Test
    void budgetUtilisationReusesBudgetServiceRatherThanRecomputingProgress() {
        stubDefaults(List.of());
        BudgetMonthResponse expected = new BudgetMonthResponse(LocalDate.of(2026, Month.JULY, 1),
                new OverallBudgetLine(1L, new BigDecimal("500.00"), new BigDecimal("120.00"),
                        new BigDecimal("380.00"), new BigDecimal("24"), false),
                List.of());
        when(budgetService.getMonthBudgets(eq(USER_ID), any(LocalDate.class))).thenReturn(expected);

        DashboardResponse response = dashboardService.getDashboard(USER_ID, 6, 5);

        assertThat(response.budgetUtilisation()).isSameAs(expected);
        verify(budgetService).getMonthBudgets(USER_ID, LocalDate.of(2026, Month.JULY, 1));
    }

    @Test
    void recentTransactionsReusesTransactionServiceSortedByMostRecentDate() {
        stubDefaults(List.of());
        List<TransactionResponse> expected = List.of();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(transactionService.listTransactions(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), isNull(),
                pageableCaptor.capture())).thenReturn(new PageImpl<>(expected));

        DashboardResponse response = dashboardService.getDashboard(USER_ID, 6, 3);

        assertThat(response.recentTransactions()).isEqualTo(expected);
        Pageable used = pageableCaptor.getValue();
        assertThat(used.getPageSize()).isEqualTo(3);
        assertThat(used).isEqualTo(PageRequest.of(0, 3, org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "transactionDate")));
    }
}
