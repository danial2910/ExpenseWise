package com.expensewise.transaction.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.transaction.dto.PatchTransactionRequest;
import com.expensewise.transaction.dto.TransactionRequest;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.dto.TransactionSummaryResponse;
import com.expensewise.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public Page<TransactionResponse> listTransactions(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @RequestParam(required = false) String type,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) String search,
                                                        @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
                                                        Pageable pageable) {
        return transactionService.listTransactions(principal.userId(), type, categoryId, from, to, search, clamp(pageable));
    }

    @GetMapping("/summary")
    public TransactionSummaryResponse getSummary(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return transactionService.getSummary(principal.userId(), type, categoryId, from, to);
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return transactionService.getTransaction(principal.userId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @Valid @RequestBody TransactionRequest request) {
        return transactionService.createTransaction(principal.userId(), request);
    }

    @PutMapping("/{id}")
    public TransactionResponse updateTransaction(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody TransactionRequest request) {
        return transactionService.updateTransaction(principal.userId(), id, request);
    }

    @PatchMapping("/{id}")
    public TransactionResponse patchTransaction(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody PatchTransactionRequest request) {
        return transactionService.patchTransaction(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        transactionService.deleteTransaction(principal.userId(), id);
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
