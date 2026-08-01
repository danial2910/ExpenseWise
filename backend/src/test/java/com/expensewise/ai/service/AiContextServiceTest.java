package com.expensewise.ai.service;

import com.expensewise.ai.dto.InsightResponse;
import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.CategoryBudgetLine;
import com.expensewise.budget.dto.OverallBudgetLine;
import com.expensewise.budget.service.BudgetService;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.dto.TransactionSummaryResponse;
import com.expensewise.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiContextServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final LocalDate MONTH = LocalDate.of(2026, 7, 1);
    private static final LocalDate MONTH_END = LocalDate.of(2026, 7, 31);

    @Mock
    private TransactionService transactionService;

    @Mock
    private BudgetService budgetService;

    private AiContextService aiContextService;

    private void setUp() {
        aiContextService = new AiContextService(transactionService, budgetService);
    }

    private TransactionResponse expense(String categoryName, String amount) {
        return new TransactionResponse(1L, "EXPENSE", new BigDecimal(amount), 10L, categoryName, "utensils",
                LocalDate.of(2026, 7, 15), "desc", Instant.now(), Instant.now());
    }

    private OverallBudgetLine overallLine(BigDecimal amount, BigDecimal spent, BigDecimal progressPercent, boolean exceeded) {
        return new OverallBudgetLine(1L, amount, spent, amount != null ? amount.subtract(spent) : null, progressPercent, exceeded);
    }

    private CategoryBudgetLine categoryLine(String name, BigDecimal amount, BigDecimal spent, BigDecimal progressPercent, boolean exceeded) {
        return new CategoryBudgetLine(10L, name, "utensils", amount != null ? 2L : null, amount, spent,
                amount != null ? amount.subtract(spent) : null, progressPercent, exceeded);
    }

    @Test
    void contextTextIncludesOnlyTheRequestingUsersData() {
        setUp();
        when(budgetService.getMonthBudgets(eq(USER_ID), isNull())).thenReturn(
                new BudgetMonthResponse(MONTH, overallLine(new BigDecimal("500.00"), new BigDecimal("100.00"), new BigDecimal("20"), false),
                        List.of(categoryLine("Food", new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100"), false))));
        when(transactionService.getSummary(eq(USER_ID), isNull(), isNull(), eq(MONTH), eq(MONTH_END)))
                .thenReturn(new TransactionSummaryResponse(new BigDecimal("2000.00"), new BigDecimal("100.00"), new BigDecimal("1900.00")));
        when(transactionService.listTransactions(eq(USER_ID), eq("EXPENSE"), isNull(), eq(MONTH), eq(MONTH_END), isNull(), any()))
                .thenReturn(pageOf(expense("Food", "100.00")));

        String context = aiContextService.buildContextText(USER_ID);

        assertThat(context).contains("Total income: RM 2000.00");
        assertThat(context).contains("Total expenses: RM 100.00");
        assertThat(context).contains("Food RM 100.00");
        assertThat(context).contains("Overall budget: RM 500.00 limit");

        verify(budgetService).getMonthBudgets(eq(USER_ID), isNull());
        verify(transactionService).getSummary(eq(USER_ID), isNull(), isNull(), eq(MONTH), eq(MONTH_END));
        verify(transactionService, org.mockito.Mockito.never())
                .getSummary(eq(OTHER_USER_ID), any(), any(), any(), any());
    }

    @Test
    void contextTextNotesWhenNoOverallBudgetIsSet() {
        setUp();
        when(budgetService.getMonthBudgets(eq(USER_ID), isNull())).thenReturn(
                new BudgetMonthResponse(MONTH, overallLine(null, BigDecimal.ZERO, null, false), List.of()));
        when(transactionService.getSummary(eq(USER_ID), isNull(), isNull(), eq(MONTH), eq(MONTH_END)))
                .thenReturn(new TransactionSummaryResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(transactionService.listTransactions(eq(USER_ID), eq("EXPENSE"), isNull(), eq(MONTH), eq(MONTH_END), isNull(), any()))
                .thenReturn(pageOf());

        String context = aiContextService.buildContextText(USER_ID);

        assertThat(context).contains("No overall budget set for this month.");
    }

    @Test
    void topCategoriesAreOrderedBySpendDescendingAndCappedAtThree() {
        setUp();
        when(budgetService.getMonthBudgets(eq(USER_ID), isNull())).thenReturn(
                new BudgetMonthResponse(MONTH, overallLine(null, BigDecimal.ZERO, null, false), List.of()));
        when(transactionService.getSummary(eq(USER_ID), isNull(), isNull(), eq(MONTH), eq(MONTH_END)))
                .thenReturn(new TransactionSummaryResponse(BigDecimal.ZERO, new BigDecimal("400.00"), new BigDecimal("-400.00")));
        when(transactionService.listTransactions(eq(USER_ID), eq("EXPENSE"), isNull(), eq(MONTH), eq(MONTH_END), isNull(), any()))
                .thenReturn(pageOf(
                        expense("Food", "50.00"),
                        expense("Transport", "200.00"),
                        expense("Bills", "80.00"),
                        expense("Shopping", "70.00")));

        String context = aiContextService.buildContextText(USER_ID);

        int transportIdx = context.indexOf("Transport");
        int billsIdx = context.indexOf("Bills");
        int shoppingIdx = context.indexOf("Shopping");
        assertThat(transportIdx).isPositive();
        assertThat(transportIdx).isLessThan(billsIdx);
        assertThat(billsIdx).isLessThan(shoppingIdx);
        assertThat(context).doesNotContain("Food RM");
    }

    // --- insights ---

    @Test
    void insightsAreEmptyWhenTheUserHasNoDataAtAll() {
        setUp();
        when(budgetService.getMonthBudgets(eq(USER_ID), isNull())).thenReturn(
                new BudgetMonthResponse(MONTH, overallLine(null, BigDecimal.ZERO, null, false), List.of()));
        when(transactionService.getSummary(eq(USER_ID), isNull(), isNull(), eq(MONTH), eq(MONTH_END)))
                .thenReturn(new TransactionSummaryResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(transactionService.listTransactions(eq(USER_ID), eq("EXPENSE"), isNull(), eq(MONTH), eq(MONTH_END), isNull(), any()))
                .thenReturn(pageOf());

        List<InsightResponse> insights = aiContextService.buildInsights(USER_ID);

        assertThat(insights).isEmpty();
    }

    @Test
    void anExceededCategoryBudgetProducesACriticalInsightBeforeAWarningOrPositiveOne() {
        setUp();
        when(budgetService.getMonthBudgets(eq(USER_ID), isNull())).thenReturn(
                new BudgetMonthResponse(MONTH,
                        overallLine(new BigDecimal("1000.00"), new BigDecimal("850.00"), new BigDecimal("85"), false),
                        List.of(categoryLine("Food", new BigDecimal("100.00"), new BigDecimal("150.00"), new BigDecimal("150"), true))));
        when(transactionService.getSummary(eq(USER_ID), isNull(), isNull(), eq(MONTH), eq(MONTH_END)))
                .thenReturn(new TransactionSummaryResponse(new BigDecimal("2000.00"), new BigDecimal("850.00"), new BigDecimal("1150.00")));
        when(transactionService.listTransactions(eq(USER_ID), eq("EXPENSE"), isNull(), eq(MONTH), eq(MONTH_END), isNull(), any()))
                .thenReturn(pageOf(expense("Food", "150.00")));

        List<InsightResponse> insights = aiContextService.buildInsights(USER_ID);

        assertThat(insights.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(insights.get(0).title()).contains("Food");
        assertThat(insights).anySatisfy(i -> assertThat(i.severity()).isEqualTo("WARNING"));
        assertThat(insights).anySatisfy(i -> assertThat(i.severity()).isEqualTo("POSITIVE"));
    }

    @Test
    void aReassuringInsightIsReturnedWhenDataExistsButNothingIsNoteworthy() {
        setUp();
        when(budgetService.getMonthBudgets(eq(USER_ID), isNull())).thenReturn(
                new BudgetMonthResponse(MONTH,
                        overallLine(new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("10"), false),
                        List.of()));
        when(transactionService.getSummary(eq(USER_ID), isNull(), isNull(), eq(MONTH), eq(MONTH_END)))
                .thenReturn(new TransactionSummaryResponse(BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("-100.00")));
        when(transactionService.listTransactions(eq(USER_ID), eq("EXPENSE"), isNull(), eq(MONTH), eq(MONTH_END), isNull(), any()))
                .thenReturn(pageOf(expense("Food", "100.00")));

        List<InsightResponse> insights = aiContextService.buildInsights(USER_ID);

        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).title()).isEqualTo("On track");
    }

    private Page<TransactionResponse> pageOf(TransactionResponse... transactions) {
        return new PageImpl<>(List.of(transactions));
    }
}
