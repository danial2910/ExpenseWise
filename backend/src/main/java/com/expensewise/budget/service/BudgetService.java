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
import com.expensewise.transaction.repository.spec.TransactionSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String EXPENSE = "EXPENSE";

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetMapper budgetMapper;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository,
                          CategoryRepository categoryRepository,
                          TransactionRepository transactionRepository,
                          BudgetMapper budgetMapper,
                          Clock clock) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetMapper = budgetMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BudgetMonthResponse getMonthBudgets(Long userId, LocalDate periodMonth) {
        LocalDate month = resolveMonth(periodMonth);

        List<Budget> budgets = budgetRepository.findByUserIdAndPeriodMonth(userId, month);
        Budget overallBudget = budgets.stream()
                .filter(b -> b.getCategoryId() == null)
                .findFirst()
                .orElse(null);
        Map<Long, Budget> categoryBudgets = budgets.stream()
                .filter(b -> b.getCategoryId() != null)
                .collect(Collectors.toMap(Budget::getCategoryId, Function.identity()));

        OverallBudgetLine overallLine = buildOverallLine(overallBudget, sumExpenses(userId, null, month));

        List<Category> expenseCategories = categoryRepository.findAllVisibleToUserByType(userId, EXPENSE);
        List<CategoryBudgetLine> categoryLines = expenseCategories.stream()
                .map(category -> buildCategoryLine(category, categoryBudgets.get(category.getId()),
                        sumExpenses(userId, category.getId(), month)))
                .toList();

        return new BudgetMonthResponse(month, overallLine, categoryLines);
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudget(Long userId, Long id) {
        return toResponse(findOwnedOrThrow(userId, id));
    }

    @Transactional
    public BudgetResponse createBudget(Long userId, BudgetRequest request) {
        requireFirstOfMonth(request.periodMonth());
        String categoryName = resolveCategoryName(userId, request.categoryId());
        requireNoDuplicate(userId, request.categoryId(), request.periodMonth(), null);
        requireWithinOverallCap(userId, request.categoryId(), request.periodMonth(), request.amount(), null);

        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategoryId(request.categoryId());
        budget.setAmount(request.amount());
        budget.setPeriodMonth(request.periodMonth());

        Budget saved = budgetRepository.save(budget);
        return toResponse(saved, categoryName);
    }

    @Transactional
    public BudgetResponse updateBudget(Long userId, Long id, BudgetRequest request) {
        Budget budget = findOwnedOrThrow(userId, id);
        requireFirstOfMonth(request.periodMonth());
        String categoryName = resolveCategoryName(userId, request.categoryId());
        requireNoDuplicate(userId, request.categoryId(), request.periodMonth(), id);
        requireWithinOverallCap(userId, request.categoryId(), request.periodMonth(), request.amount(), id);

        budget.setCategoryId(request.categoryId());
        budget.setAmount(request.amount());
        budget.setPeriodMonth(request.periodMonth());

        return toResponse(budget, categoryName);
    }

    @Transactional
    public BudgetResponse patchBudget(Long userId, Long id, PatchBudgetRequest request) {
        Budget budget = findOwnedOrThrow(userId, id);

        Long categoryId = request.categoryId() != null ? request.categoryId() : budget.getCategoryId();
        LocalDate periodMonth = request.periodMonth() != null ? request.periodMonth() : budget.getPeriodMonth();
        BigDecimal amount = request.amount() != null ? request.amount() : budget.getAmount();

        requireFirstOfMonth(periodMonth);
        String categoryName = resolveCategoryName(userId, categoryId);
        requireNoDuplicate(userId, categoryId, periodMonth, id);
        requireWithinOverallCap(userId, categoryId, periodMonth, amount, id);

        budget.setCategoryId(categoryId);
        budget.setPeriodMonth(periodMonth);
        budget.setAmount(amount);

        return toResponse(budget, categoryName);
    }

    @Transactional
    public void deleteBudget(Long userId, Long id) {
        Budget budget = findOwnedOrThrow(userId, id);
        if (budget.getCategoryId() == null) {
            boolean hasCategoryBudgets = budgetRepository.findByUserIdAndPeriodMonth(userId, budget.getPeriodMonth())
                    .stream()
                    .anyMatch(b -> b.getCategoryId() != null);
            if (hasCategoryBudgets) {
                throw new OverallBudgetInUseException(
                        "Delete this month's category budgets before removing the overall budget");
            }
        }
        budgetRepository.delete(budget);
    }

    private LocalDate resolveMonth(LocalDate periodMonth) {
        if (periodMonth != null) {
            requireFirstOfMonth(periodMonth);
            return periodMonth;
        }
        return LocalDate.now(clock.withZone(KL_ZONE)).withDayOfMonth(1);
    }

    private void requireFirstOfMonth(LocalDate periodMonth) {
        if (!periodMonth.equals(periodMonth.withDayOfMonth(1))) {
            throw new InvalidBudgetPeriodException("Period month must be the first day of the month");
        }
    }

    /**
     * Returns the category's name when categoryId is set (after validating
     * it's visible to the caller and an EXPENSE category — budgets cap
     * spending, not income), or null for the overall budget.
     */
    private String resolveCategoryName(Long userId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidBudgetCategoryException("Category not found"));
        boolean visible = category.getUserId() == null || category.getUserId().equals(userId);
        if (!visible) {
            throw new InvalidBudgetCategoryException("Category not found");
        }
        if (!EXPENSE.equals(category.getType())) {
            throw new InvalidBudgetCategoryException("Budgets can only be set on expense categories");
        }
        return category.getName();
    }

    /**
     * The DB's two partial unique indexes are the source of truth, but a
     * clean pre-check gives a 400 VALIDATION_FAILED instead of surfacing a
     * raw constraint violation as a 500.
     */
    private void requireNoDuplicate(Long userId, Long categoryId, LocalDate periodMonth, Long excludeId) {
        boolean duplicate = budgetRepository.findByUserIdAndPeriodMonth(userId, periodMonth).stream()
                .anyMatch(b -> !Objects.equals(b.getId(), excludeId) && Objects.equals(b.getCategoryId(), categoryId));
        if (duplicate) {
            throw new DuplicateBudgetException("A budget already exists for this category and month");
        }
    }

    /**
     * Category budgets are capped against the overall budget in both
     * directions: a category budget (creating/setting categoryId != null)
     * requires an overall budget to already exist for the month, and its
     * amount plus every other category budget's amount must not exceed the
     * overall amount; the overall budget itself (categoryId == null)
     * cannot be set/edited below the sum of category budgets already set.
     * excludeId is the budget being edited (null on create), so it isn't
     * counted against itself.
     */
    private void requireWithinOverallCap(Long userId, Long categoryId, LocalDate periodMonth, BigDecimal amount,
                                          Long excludeId) {
        List<Budget> monthBudgets = budgetRepository.findByUserIdAndPeriodMonth(userId, periodMonth);

        if (categoryId != null) {
            Budget overall = monthBudgets.stream()
                    .filter(b -> b.getCategoryId() == null)
                    .findFirst()
                    .orElseThrow(() -> new OverallBudgetRequiredException(
                            "Set an overall budget for this month before adding category budgets"));

            BigDecimal otherCategoriesSum = monthBudgets.stream()
                    .filter(b -> b.getCategoryId() != null && !Objects.equals(b.getId(), excludeId))
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (otherCategoriesSum.add(amount).compareTo(overall.getAmount()) > 0) {
                throw new BudgetExceedsOverallException(
                        "Category budgets cannot exceed the overall monthly budget");
            }
        } else {
            BigDecimal categoriesSum = monthBudgets.stream()
                    .filter(b -> b.getCategoryId() != null && !Objects.equals(b.getId(), excludeId))
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (amount.compareTo(categoriesSum) < 0) {
                throw new BudgetExceedsOverallException(
                        "Overall budget cannot be less than the sum of category budgets already set");
            }
        }
    }

    private BigDecimal sumExpenses(Long userId, Long categoryId, LocalDate month) {
        LocalDate from = month;
        LocalDate to = month.withDayOfMonth(month.lengthOfMonth());
        Specification<Transaction> spec = Specification.where(TransactionSpecifications.ownedBy(userId))
                .and(TransactionSpecifications.hasType(EXPENSE))
                .and(TransactionSpecifications.hasCategory(categoryId))
                .and(TransactionSpecifications.dateFrom(from))
                .and(TransactionSpecifications.dateTo(to));

        // Transaction volumes are small for a solo-user demo app (see
        // TransactionService.getSummary) — summing in Java with BigDecimal
        // avoids a second aggregate-query code path to keep in sync.
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : transactionRepository.findAll(spec)) {
            total = total.add(transaction.getAmount());
        }
        return total;
    }

    private OverallBudgetLine buildOverallLine(Budget budget, BigDecimal spent) {
        BigDecimal amount = budget != null ? budget.getAmount() : null;
        BudgetComputation computation = compute(amount, spent);
        return new OverallBudgetLine(budget != null ? budget.getId() : null, amount, spent,
                computation.remaining(), computation.progressPercent(), computation.exceeded());
    }

    private CategoryBudgetLine buildCategoryLine(Category category, Budget budget, BigDecimal spent) {
        BigDecimal amount = budget != null ? budget.getAmount() : null;
        BudgetComputation computation = compute(amount, spent);
        return new CategoryBudgetLine(category.getId(), category.getName(), category.getIcon(),
                budget != null ? budget.getId() : null, amount, spent,
                computation.remaining(), computation.progressPercent(), computation.exceeded());
    }

    private record BudgetComputation(BigDecimal remaining, BigDecimal progressPercent, boolean exceeded) {
    }

    /**
     * No stored limit means no remaining/progress to show (null, not zero)
     * and never "exceeded" — an unset budget can't be over budget.
     */
    private BudgetComputation compute(BigDecimal amount, BigDecimal spent) {
        if (amount == null) {
            return new BudgetComputation(null, null, false);
        }
        BigDecimal remaining = amount.subtract(spent);
        BigDecimal progressPercent = spent.multiply(BigDecimal.valueOf(100)).divide(amount, 0, RoundingMode.HALF_UP);
        boolean exceeded = spent.compareTo(amount) > 0;
        return new BudgetComputation(remaining, progressPercent, exceeded);
    }

    private BudgetResponse toResponse(Budget budget) {
        String categoryName = budget.getCategoryId() != null
                ? categoryRepository.findById(budget.getCategoryId()).map(Category::getName).orElse(null)
                : null;
        return toResponse(budget, categoryName);
    }

    private BudgetResponse toResponse(Budget budget, String categoryName) {
        BigDecimal spent = sumExpenses(budget.getUserId(), budget.getCategoryId(), budget.getPeriodMonth());
        BudgetComputation computation = compute(budget.getAmount(), spent);
        return budgetMapper.toResponse(budget, categoryName, spent, computation.remaining(),
                computation.progressPercent(), computation.exceeded());
    }

    /**
     * Only the caller's own budgets can be read or mutated — another
     * user's budget is reported as 404, not 403, matching
     * TransactionService/CategoryService's convention.
     */
    private Budget findOwnedOrThrow(Long userId, Long id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
        if (!budget.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Budget not found");
        }
        return budget;
    }
}
