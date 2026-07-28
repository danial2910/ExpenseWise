package com.expensewise.budget.service;

import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.budget.dto.BudgetResponse;
import com.expensewise.budget.dto.CategoryBudgetLine;
import com.expensewise.budget.dto.OverallBudgetLine;
import com.expensewise.budget.dto.PatchBudgetRequest;
import com.expensewise.budget.entity.Budget;
import com.expensewise.budget.mapper.BudgetMapper;
import com.expensewise.budget.repository.BudgetRepository;
import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.exception.BudgetExceedsOverallException;
import com.expensewise.exception.DuplicateBudgetException;
import com.expensewise.exception.InvalidBudgetCategoryException;
import com.expensewise.exception.InvalidBudgetPeriodException;
import com.expensewise.exception.OverallBudgetInUseException;
import com.expensewise.exception.OverallBudgetRequiredException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetMapper budgetMapper;

    private BudgetService budgetService;

    /**
     * Fixed at 2026-07-15 12:00 UTC, which is 2026-07-15 20:00 in
     * Asia/Kuala_Lumpur — same calendar month either way, so tests that
     * need a genuine UTC/KL day-boundary mismatch pick a time near
     * midnight KL instead (see klThisMonthDefault... tests below).
     */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, categoryRepository, transactionRepository,
                budgetMapper, FIXED_CLOCK);
        lenient().when(budgetMapper.toResponse(any(Budget.class), any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    Budget budget = invocation.getArgument(0);
                    String categoryName = invocation.getArgument(1);
                    BigDecimal spent = invocation.getArgument(2);
                    BigDecimal remaining = invocation.getArgument(3);
                    BigDecimal progressPercent = invocation.getArgument(4);
                    boolean exceeded = invocation.getArgument(5);
                    return new BudgetResponse(budget.getId(), budget.getCategoryId(), categoryName,
                            budget.getAmount(), budget.getPeriodMonth(), spent, remaining, progressPercent,
                            exceeded, budget.getCreatedAt());
                });
    }

    private Category expenseCategory() {
        Category category = new Category();
        category.setId(10L);
        category.setUserId(null);
        category.setName("Food");
        category.setType("EXPENSE");
        category.setIcon("utensils");
        category.setSystem(true);
        return category;
    }

    private Category incomeCategory() {
        Category category = new Category();
        category.setId(11L);
        category.setUserId(null);
        category.setName("Salary");
        category.setType("INCOME");
        category.setIcon("trending-up");
        category.setSystem(true);
        return category;
    }

    private Category otherUsersCategory() {
        Category category = new Category();
        category.setId(30L);
        category.setUserId(OTHER_USER_ID);
        category.setName("Side Hustle");
        category.setType("EXPENSE");
        category.setIcon("freelance");
        category.setSystem(false);
        return category;
    }

    private Budget ownOverallBudget() {
        Budget budget = new Budget();
        budget.setId(100L);
        budget.setUserId(USER_ID);
        budget.setCategoryId(null);
        budget.setAmount(new BigDecimal("500.00"));
        budget.setPeriodMonth(LocalDate.of(2026, 7, 1));
        return budget;
    }

    private Budget othersBudget() {
        Budget budget = new Budget();
        budget.setId(200L);
        budget.setUserId(OTHER_USER_ID);
        budget.setCategoryId(null);
        budget.setAmount(new BigDecimal("500.00"));
        budget.setPeriodMonth(LocalDate.of(2026, 7, 1));
        return budget;
    }

    private Transaction expense(String amount) {
        Transaction transaction = new Transaction();
        transaction.setType("EXPENSE");
        transaction.setAmount(new BigDecimal(amount));
        return transaction;
    }

    // --- ownership ---

    @Test
    void getBudgetHidesAnotherUsersBudgetAs404() {
        when(budgetRepository.findById(200L)).thenReturn(Optional.of(othersBudget()));

        assertThatThrownBy(() -> budgetService.getBudget(USER_ID, 200L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateBudgetOnAnotherUsersBudgetIsRejectedAs404() {
        when(budgetRepository.findById(200L)).thenReturn(Optional.of(othersBudget()));

        assertThatThrownBy(() -> budgetService.updateBudget(USER_ID, 200L,
                new BudgetRequest(new BigDecimal("100.00"), null, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteBudgetOnAnotherUsersBudgetIsRejectedAs404() {
        when(budgetRepository.findById(200L)).thenReturn(Optional.of(othersBudget()));

        assertThatThrownBy(() -> budgetService.deleteBudget(USER_ID, 200L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(budgetRepository, never()).delete(any(Budget.class));
    }

    // --- category coherence ---

    @Test
    void createBudgetRejectsAnIncomeCategory() {
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(incomeCategory()));

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("100.00"), 11L, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(InvalidBudgetCategoryException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void createBudgetRejectsAnotherUsersPrivateCategory() {
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(otherUsersCategory()));

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("100.00"), 30L, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(InvalidBudgetCategoryException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void createBudgetRejectsAMissingCategory() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("100.00"), 99L, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(InvalidBudgetCategoryException.class);
    }

    @Test
    void createBudgetSavesForAVisibleExpenseCategory() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));
        // A category budget requires an overall budget to already exist for the month.
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(ownOverallBudget()));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        BudgetResponse response = budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("300.00"), 10L, LocalDate.of(2026, 7, 1)));

        assertThat(response.categoryName()).isEqualTo("Food");
        assertThat(response.amount()).isEqualByComparingTo("300.00");
    }

    @Test
    void createBudgetRejectsACategoryBudgetWhenNoOverallBudgetExistsYet() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1))).thenReturn(List.of());

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("100.00"), 10L, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(OverallBudgetRequiredException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void createBudgetRejectsACategoryBudgetThatWouldExceedTheOverallBudget() {
        // Overall is 500.00; Transport already takes 400.00, leaving only 100.00 headroom.
        Budget transportBudget = new Budget();
        transportBudget.setId(102L);
        transportBudget.setUserId(USER_ID);
        transportBudget.setCategoryId(12L);
        transportBudget.setAmount(new BigDecimal("400.00"));
        transportBudget.setPeriodMonth(LocalDate.of(2026, 7, 1));

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(ownOverallBudget(), transportBudget));

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("150.00"), 10L, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(BudgetExceedsOverallException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void updateBudgetRejectsLoweringTheOverallBudgetBelowTheSumOfCategoryBudgets() {
        Budget overall = ownOverallBudget(); // 500.00
        Budget foodBudget = new Budget();
        foodBudget.setId(101L);
        foodBudget.setUserId(USER_ID);
        foodBudget.setCategoryId(10L);
        foodBudget.setAmount(new BigDecimal("300.00"));
        foodBudget.setPeriodMonth(LocalDate.of(2026, 7, 1));

        when(budgetRepository.findById(100L)).thenReturn(Optional.of(overall));
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(overall, foodBudget));

        assertThatThrownBy(() -> budgetService.updateBudget(USER_ID, 100L,
                new BudgetRequest(new BigDecimal("200.00"), null, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(BudgetExceedsOverallException.class);
    }

    @Test
    void deleteBudgetRejectsDeletingTheOverallBudgetWhileCategoryBudgetsStillExist() {
        Budget overall = ownOverallBudget();
        Budget foodBudget = new Budget();
        foodBudget.setId(101L);
        foodBudget.setUserId(USER_ID);
        foodBudget.setCategoryId(10L);
        foodBudget.setAmount(new BigDecimal("100.00"));
        foodBudget.setPeriodMonth(LocalDate.of(2026, 7, 1));

        when(budgetRepository.findById(100L)).thenReturn(Optional.of(overall));
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(overall, foodBudget));

        assertThatThrownBy(() -> budgetService.deleteBudget(USER_ID, 100L))
                .isInstanceOf(OverallBudgetInUseException.class);

        verify(budgetRepository, never()).delete(any(Budget.class));
    }

    // --- period validation ---

    @Test
    void createBudgetRejectsAPeriodMonthThatIsNotTheFirstOfTheMonth() {
        assertThatThrownBy(() -> budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("100.00"), null, LocalDate.of(2026, 7, 15))))
                .isInstanceOf(InvalidBudgetPeriodException.class);

        verify(budgetRepository, never()).save(any());
    }

    // --- uniqueness ---

    @Test
    void createBudgetRejectsADuplicateOverallBudgetForTheSameMonth() {
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(ownOverallBudget()));

        assertThatThrownBy(() -> budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("100.00"), null, LocalDate.of(2026, 7, 1))))
                .isInstanceOf(DuplicateBudgetException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void createBudgetAllowsACategoryBudgetAlongsideAnExistingOverallBudgetForTheSameMonth() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(ownOverallBudget()));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        BudgetResponse response = budgetService.createBudget(USER_ID,
                new BudgetRequest(new BigDecimal("200.00"), 10L, LocalDate.of(2026, 7, 1)));

        assertThat(response.categoryId()).isEqualTo(10L);
    }

    @Test
    void updateBudgetAllowsKeepingItsOwnSlotWithoutTrippingTheDuplicateCheck() {
        Budget existing = ownOverallBudget();
        when(budgetRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(existing));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        BudgetResponse response = budgetService.updateBudget(USER_ID, 100L,
                new BudgetRequest(new BigDecimal("600.00"), null, LocalDate.of(2026, 7, 1)));

        assertThat(response.amount()).isEqualByComparingTo("600.00");
    }

    // --- patch ---

    @Test
    void patchBudgetOnlyChangesTheAmountWhenOnlyAmountIsProvided() {
        Budget existing = ownOverallBudget();
        when(budgetRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(existing));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        budgetService.patchBudget(USER_ID, 100L, new PatchBudgetRequest(new BigDecimal("450.00"), null, null));

        assertThat(existing.getAmount()).isEqualByComparingTo("450.00");
        assertThat(existing.getPeriodMonth()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    // --- computation: spent / remaining / progress / exceeded ---

    @Test
    void computationRoundsProgressPercentToTheNearestWholeNumber() {
        Budget existing = ownOverallBudget(); // limit 500.00
        when(budgetRepository.findById(100L)).thenReturn(Optional.of(existing));
        // 333.33 / 500.00 = 66.666% -> rounds to 67 with HALF_UP.
        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(expense("333.33")));

        BudgetResponse response = budgetService.getBudget(USER_ID, 100L);

        assertThat(response.spent()).isEqualByComparingTo("333.33");
        assertThat(response.remaining()).isEqualByComparingTo("166.67");
        assertThat(response.progressPercent()).isEqualByComparingTo("67");
        assertThat(response.exceeded()).isFalse();
    }

    @Test
    void computationFlagsExceededAndAllowsANegativeRemaining() {
        Budget existing = ownOverallBudget(); // limit 500.00
        when(budgetRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(expense("300.00"), expense("250.00")));

        BudgetResponse response = budgetService.getBudget(USER_ID, 100L);

        assertThat(response.spent()).isEqualByComparingTo("550.00");
        assertThat(response.remaining()).isEqualByComparingTo("-50.00");
        assertThat(response.exceeded()).isTrue();
    }

    @Test
    void computationWithNoBudgetSetHasNullRemainingAndProgressAndIsNeverExceeded() {
        // getMonthBudgets exercises the "no budget for this category" path.
        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1))).thenReturn(List.of());
        when(categoryRepository.findAllVisibleToUserByType(USER_ID, "EXPENSE"))
                .thenReturn(List.of(expenseCategory()));
        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(expense("40.00")));

        BudgetMonthResponse response = budgetService.getMonthBudgets(USER_ID, LocalDate.of(2026, 7, 1));

        assertThat(response.overall().amount()).isNull();
        assertThat(response.overall().remaining()).isNull();
        assertThat(response.overall().progressPercent()).isNull();
        assertThat(response.overall().exceeded()).isFalse();

        CategoryBudgetLine foodLine = response.categories().get(0);
        assertThat(foodLine.budgetId()).isNull();
        assertThat(foodLine.spent()).isEqualByComparingTo("40.00");
        assertThat(foodLine.remaining()).isNull();
    }

    // --- overall vs category scoping ---

    @Test
    void monthViewScopesOverallSpendingToAllCategoriesAndCategorySpendingToItsOwnCategory() {
        Budget overallBudget = ownOverallBudget();
        Budget foodBudget = new Budget();
        foodBudget.setId(101L);
        foodBudget.setUserId(USER_ID);
        foodBudget.setCategoryId(10L);
        foodBudget.setAmount(new BigDecimal("100.00"));
        foodBudget.setPeriodMonth(LocalDate.of(2026, 7, 1));

        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(overallBudget, foodBudget));
        when(categoryRepository.findAllVisibleToUserByType(USER_ID, "EXPENSE"))
                .thenReturn(List.of(expenseCategory()));
        // First call (overall, no category filter) sums everything; second
        // call (category-scoped) returns only Food's spending. Order
        // matches BudgetService.getMonthBudgets: overall first, then per
        // category.
        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(expense("80.00"), expense("20.00")))
                .thenReturn(List.of(expense("80.00")));

        BudgetMonthResponse response = budgetService.getMonthBudgets(USER_ID, LocalDate.of(2026, 7, 1));

        assertThat(response.overall().spent()).isEqualByComparingTo("100.00");
        assertThat(response.categories().get(0).spent()).isEqualByComparingTo("80.00");
    }

    // --- KL "this month" boundary ---

    @Test
    void defaultMonthIsResolvedInAsiaKualaLumpurNotUtc() {
        // 2026-07-31 23:00 UTC is already 2026-08-01 07:00 in
        // Asia/Kuala_Lumpur (UTC+8) — the KL-anchored default must resolve
        // to August, not July, proving the boundary isn't computed in UTC.
        Clock lateJulyUtc = Clock.fixed(Instant.parse("2026-07-31T23:00:00Z"), ZoneId.of("UTC"));
        BudgetService klService = new BudgetService(budgetRepository, categoryRepository, transactionRepository,
                budgetMapper, lateJulyUtc);

        when(budgetRepository.findByUserIdAndPeriodMonth(USER_ID, LocalDate.of(2026, 8, 1))).thenReturn(List.of());
        when(categoryRepository.findAllVisibleToUserByType(USER_ID, "EXPENSE")).thenReturn(List.of());
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        BudgetMonthResponse response = klService.getMonthBudgets(USER_ID, null);

        assertThat(response.periodMonth()).isEqualTo(LocalDate.of(2026, 8, 1));
    }
}
