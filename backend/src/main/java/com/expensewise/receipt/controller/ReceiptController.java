package com.expensewise.receipt.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.RequiresFeature;
import com.expensewise.receipt.service.ReceiptService;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.service.TransactionService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nested under /transactions/{id}/receipt, not its own top-level resource —
 * a receipt has no independent identity the API exposes; it's always
 * addressed through its transaction (CLAUDE.md: receipts are not a
 * standalone page). Gated under Feature.TRANSACTIONS, same reasoning as the
 * Recurring module: receipts belong to transactions, no dedicated feature
 * flag exists or is needed for them.
 */
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/receipt")
@RequiresFeature(Feature.TRANSACTIONS)
public class ReceiptController {

    private final ReceiptService receiptService;
    private final TransactionService transactionService;

    public ReceiptController(ReceiptService receiptService, TransactionService transactionService) {
        this.receiptService = receiptService;
        this.transactionService = transactionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransactionResponse uploadReceipt(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable Long transactionId,
                                              @RequestParam("file") MultipartFile file) {
        receiptService.uploadReceipt(principal.userId(), transactionId, file);
        return transactionService.getTransaction(principal.userId(), transactionId);
    }

    @DeleteMapping
    public TransactionResponse removeReceipt(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable Long transactionId) {
        receiptService.removeReceipt(principal.userId(), transactionId);
        return transactionService.getTransaction(principal.userId(), transactionId);
    }
}
