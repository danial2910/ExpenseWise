package com.expensewise.transaction.service;

import com.expensewise.category.entity.Category;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.exception.InvalidTransactionCategoryException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.receipt.service.ReceiptService;
import com.expensewise.transaction.dto.PatchTransactionRequest;
import com.expensewise.transaction.dto.TransactionRequest;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.dto.TransactionSummaryResponse;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.mapper.TransactionMapper;
import com.expensewise.transaction.repository.TransactionRepository;
import com.expensewise.transaction.repository.spec.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionMapper transactionMapper;
    private final ReceiptService receiptService;

    public TransactionService(TransactionRepository transactionRepository,
                               CategoryRepository categoryRepository,
                               TransactionMapper transactionMapper,
                               ReceiptService receiptService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionMapper = transactionMapper;
        this.receiptService = receiptService;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(Long userId, String type, Long categoryId,
                                                        LocalDate from, LocalDate to, String search,
                                                        Pageable pageable) {
        Specification<Transaction> spec = buildFilterSpec(userId, type, categoryId, from, to, search);
        Page<Transaction> page = transactionRepository.findAll(spec, pageable);
        Map<Long, Category> categoriesById = loadCategories(page.getContent());
        Map<Long, String> receiptUrlsById = receiptService.findReceiptUrls(
                page.getContent().stream().map(Transaction::getId).toList());
        return page.map(tx -> transactionMapper.toResponse(tx, categoriesById.get(tx.getCategoryId()),
                receiptUrlsById.get(tx.getId())));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long userId, Long id) {
        Transaction transaction = findOwnedOrThrow(userId, id);
        Category category = categoryRepository.findById(transaction.getCategoryId()).orElse(null);
        return transactionMapper.toResponse(transaction, category, receiptService.findReceiptUrl(id));
    }

    @Transactional
    public TransactionResponse createTransaction(Long userId, TransactionRequest request) {
        Category category = requireCoherentCategory(userId, request.categoryId(), request.type());

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setCategoryId(request.categoryId());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setDescription(request.description());

        Transaction saved = transactionRepository.save(transaction);
        // A brand new transaction can never already have a receipt.
        return transactionMapper.toResponse(saved, category, null);
    }

    @Transactional
    public TransactionResponse updateTransaction(Long userId, Long id, TransactionRequest request) {
        Transaction transaction = findOwnedOrThrow(userId, id);
        Category category = requireCoherentCategory(userId, request.categoryId(), request.type());

        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setCategoryId(request.categoryId());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setDescription(request.description());

        // Editing a transaction's fields never touches its receipt (a
        // separate endpoint), but the response must still reflect one if
        // it already exists — otherwise saving the edit form would look
        // like it silently dropped the attachment.
        return transactionMapper.toResponse(transaction, category, receiptService.findReceiptUrl(id));
    }

    @Transactional
    public TransactionResponse patchTransaction(Long userId, Long id, PatchTransactionRequest request) {
        Transaction transaction = findOwnedOrThrow(userId, id);

        String type = request.type() != null ? request.type() : transaction.getType();
        Long categoryId = request.categoryId() != null ? request.categoryId() : transaction.getCategoryId();
        Category category = requireCoherentCategory(userId, categoryId, type);

        transaction.setType(type);
        transaction.setCategoryId(categoryId);
        if (request.amount() != null) {
            transaction.setAmount(request.amount());
        }
        if (request.transactionDate() != null) {
            transaction.setTransactionDate(request.transactionDate());
        }
        if (request.description() != null) {
            transaction.setDescription(request.description());
        }

        return transactionMapper.toResponse(transaction, category, receiptService.findReceiptUrl(id));
    }

    @Transactional
    public void deleteTransaction(Long userId, Long id) {
        Transaction transaction = findOwnedOrThrow(userId, id);
        // The receipts row cascades away with the FK automatically, but the
        // object in Supabase Storage does not — delete it explicitly first
        // or it's orphaned with nothing left pointing at it.
        receiptService.deleteReceiptForTransaction(id);
        transactionRepository.delete(transaction);
    }

    /**
     * Sums exactly the same filter set the transaction list uses — no
     * independent "this month" default. An earlier version defaulted the
     * summary to the current month when from/to were both omitted, which
     * silently diverged from the list (which shows all-time by default),
     * making the summary strip look wrong for older transactions. See
     * DECISIONS.md.
     */
    @Transactional(readOnly = true)
    public TransactionSummaryResponse getSummary(Long userId, String type, Long categoryId,
                                                  LocalDate from, LocalDate to) {
        Specification<Transaction> spec = buildFilterSpec(userId, type, categoryId, from, to, null);
        List<Transaction> transactions = transactionRepository.findAll(spec);

        // Transaction volumes are small for a solo-user demo app, so summing
        // in Java with BigDecimal is simple, easy to explain, and avoids a
        // second aggregate-query code path to keep in sync with the filters.
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            if ("INCOME".equals(transaction.getType())) {
                totalIncome = totalIncome.add(transaction.getAmount());
            } else {
                totalExpense = totalExpense.add(transaction.getAmount());
            }
        }
        BigDecimal balance = totalIncome.subtract(totalExpense);

        return new TransactionSummaryResponse(totalIncome, totalExpense, balance);
    }

    private Specification<Transaction> buildFilterSpec(Long userId, String type, Long categoryId,
                                                         LocalDate from, LocalDate to, String search) {
        return Specification.where(TransactionSpecifications.ownedBy(userId))
                .and(TransactionSpecifications.hasType(type))
                .and(TransactionSpecifications.hasCategory(categoryId))
                .and(TransactionSpecifications.dateFrom(from))
                .and(TransactionSpecifications.dateTo(to))
                .and(TransactionSpecifications.descriptionContains(search));
    }

    private Map<Long, Category> loadCategories(List<Transaction> transactions) {
        List<Long> categoryIds = transactions.stream()
                .map(Transaction::getCategoryId)
                .distinct()
                .collect(Collectors.toList());
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /**
     * The transaction's type must equal the referenced category's type, and
     * the category must be visible to the caller (a system category, or one
     * they own themselves) — mirrors CategoryService's visibility check.
     * Both a foreign private category and a type mismatch surface as the
     * same clean 400 on the categoryId field, not a 500.
     */
    private Category requireCoherentCategory(Long userId, Long categoryId, String type) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidTransactionCategoryException("Category not found"));
        boolean visible = category.getUserId() == null || category.getUserId().equals(userId);
        if (!visible) {
            throw new InvalidTransactionCategoryException("Category not found");
        }
        if (!category.getType().equals(type)) {
            throw new InvalidTransactionCategoryException("Transaction type must match the category's type");
        }
        return category;
    }

    /**
     * Only the caller's own transactions can be read or mutated — another
     * user's transaction is reported as 404, not 403, so its existence
     * isn't leaked (same convention as CategoryService.findOwnedOrThrow).
     */
    private Transaction findOwnedOrThrow(Long userId, Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!transaction.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Transaction not found");
        }
        return transaction;
    }
}
