package com.expensewise.transaction.service;

import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.exception.InvalidTransactionCategoryException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.transaction.dto.PatchTransactionRequest;
import com.expensewise.transaction.dto.TransactionRequest;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.dto.TransactionSummaryResponse;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.mapper.TransactionMapper;
import com.expensewise.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionMapper transactionMapper;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, categoryRepository, transactionMapper);
        lenient().when(transactionMapper.toResponse(any(Transaction.class), any())).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            Category category = invocation.getArgument(1);
            return new TransactionResponse(
                    transaction.getId(),
                    transaction.getType(),
                    transaction.getAmount(),
                    transaction.getCategoryId(),
                    category != null ? category.getName() : null,
                    category != null ? category.getIcon() : null,
                    transaction.getTransactionDate(),
                    transaction.getDescription(),
                    transaction.getCreatedAt(),
                    transaction.getUpdatedAt());
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
        category.setType("INCOME");
        category.setIcon("freelance");
        category.setSystem(false);
        return category;
    }

    private Transaction ownTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(100L);
        transaction.setUserId(USER_ID);
        transaction.setType("EXPENSE");
        transaction.setAmount(new BigDecimal("25.00"));
        transaction.setCategoryId(10L);
        transaction.setTransactionDate(LocalDate.of(2026, 7, 15));
        transaction.setDescription("Lunch");
        return transaction;
    }

    private Transaction othersTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(200L);
        transaction.setUserId(OTHER_USER_ID);
        transaction.setType("EXPENSE");
        transaction.setAmount(new BigDecimal("40.00"));
        transaction.setCategoryId(10L);
        transaction.setTransactionDate(LocalDate.of(2026, 7, 15));
        return transaction;
    }

    // --- ownership ---

    @Test
    void getTransactionHidesAnotherUsersTransactionAs404() {
        when(transactionRepository.findById(200L)).thenReturn(Optional.of(othersTransaction()));

        assertThatThrownBy(() -> transactionService.getTransaction(USER_ID, 200L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTransactionOnAnotherUsersTransactionIsRejectedAs404() {
        when(transactionRepository.findById(200L)).thenReturn(Optional.of(othersTransaction()));

        assertThatThrownBy(() -> transactionService.updateTransaction(USER_ID, 200L,
                new TransactionRequest("EXPENSE", new BigDecimal("10.00"), 10L, LocalDate.now(), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTransactionOnAnotherUsersTransactionIsRejectedAs404() {
        when(transactionRepository.findById(200L)).thenReturn(Optional.of(othersTransaction()));

        assertThatThrownBy(() -> transactionService.deleteTransaction(USER_ID, 200L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    // --- category coherence ---

    @Test
    void createTransactionRejectsATypeThatDoesNotMatchTheCategorysType() {
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(incomeCategory()));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID,
                new TransactionRequest("EXPENSE", new BigDecimal("10.00"), 11L, LocalDate.now(), null)))
                .isInstanceOf(InvalidTransactionCategoryException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransactionRejectsAnotherUsersPrivateCategory() {
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(otherUsersCategory()));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID,
                new TransactionRequest("INCOME", new BigDecimal("10.00"), 30L, LocalDate.now(), null)))
                .isInstanceOf(InvalidTransactionCategoryException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransactionRejectsAMissingCategory() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID,
                new TransactionRequest("EXPENSE", new BigDecimal("10.00"), 99L, LocalDate.now(), null)))
                .isInstanceOf(InvalidTransactionCategoryException.class);
    }

    @Test
    void createTransactionSavesWhenTypeMatchesTheCategorysType() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.createTransaction(USER_ID,
                new TransactionRequest("EXPENSE", new BigDecimal("25.00"), 10L, LocalDate.of(2026, 7, 15), "Lunch"));

        assertThat(response.categoryName()).isEqualTo("Food");
        assertThat(response.amount()).isEqualByComparingTo("25.00");
    }

    // --- CRUD ---

    @Test
    void updateTransactionAppliesAllFieldsFromTheRequest() {
        Transaction existing = ownTransaction();
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(incomeCategory()));

        transactionService.updateTransaction(USER_ID, 100L,
                new TransactionRequest("INCOME", new BigDecimal("500.00"), 11L, LocalDate.of(2026, 7, 20), "Bonus"));

        assertThat(existing.getType()).isEqualTo("INCOME");
        assertThat(existing.getAmount()).isEqualByComparingTo("500.00");
        assertThat(existing.getCategoryId()).isEqualTo(11L);
        assertThat(existing.getTransactionDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(existing.getDescription()).isEqualTo("Bonus");
    }

    @Test
    void patchTransactionOnlyChangesTheFieldsProvided() {
        Transaction existing = ownTransaction();
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory()));

        transactionService.patchTransaction(USER_ID, 100L,
                new PatchTransactionRequest(null, new BigDecimal("30.00"), null, null, null));

        assertThat(existing.getAmount()).isEqualByComparingTo("30.00");
        assertThat(existing.getType()).isEqualTo("EXPENSE");
        assertThat(existing.getDescription()).isEqualTo("Lunch");
        assertThat(existing.getTransactionDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void deleteTransactionRemovesTheCallersOwnTransaction() {
        Transaction existing = ownTransaction();
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(existing));

        transactionService.deleteTransaction(USER_ID, 100L);

        verify(transactionRepository).delete(existing);
    }

    // --- summary / balance ---

    @Test
    void summarySumsIncomeAndExpenseSeparatelyAndComputesBalance() {
        Transaction income1 = new Transaction();
        income1.setType("INCOME");
        income1.setAmount(new BigDecimal("1000.00"));

        Transaction income2 = new Transaction();
        income2.setType("INCOME");
        income2.setAmount(new BigDecimal("250.50"));

        Transaction expense1 = new Transaction();
        expense1.setType("EXPENSE");
        expense1.setAmount(new BigDecimal("300.25"));

        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(income1, income2, expense1));

        TransactionSummaryResponse summary = transactionService.getSummary(USER_ID, null, null,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(summary.totalIncome()).isEqualByComparingTo("1250.50");
        assertThat(summary.totalExpense()).isEqualByComparingTo("300.25");
        assertThat(summary.balance()).isEqualByComparingTo("950.25");
    }

    @Test
    void summaryWithNoTransactionsReturnsZeroes() {
        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        TransactionSummaryResponse summary = transactionService.getSummary(USER_ID, null, null,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(summary.totalIncome()).isEqualByComparingTo("0");
        assertThat(summary.totalExpense()).isEqualByComparingTo("0");
        assertThat(summary.balance()).isEqualByComparingTo("0");
    }

    @Test
    void summaryWithNoDateFilterSumsAllTimeJustLikeTheUnfilteredList() {
        Transaction oldIncome = new Transaction();
        oldIncome.setType("INCOME");
        oldIncome.setAmount(new BigDecimal("800.00"));

        Transaction oldExpense = new Transaction();
        oldExpense.setType("EXPENSE");
        oldExpense.setAmount(new BigDecimal("20.00"));

        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(oldIncome, oldExpense));

        TransactionSummaryResponse summary = transactionService.getSummary(USER_ID, null, null, null, null);

        assertThat(summary.totalIncome()).isEqualByComparingTo("800.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("20.00");
        assertThat(summary.balance()).isEqualByComparingTo("780.00");
    }
}
