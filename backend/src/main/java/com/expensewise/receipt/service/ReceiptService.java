package com.expensewise.receipt.service;

import com.expensewise.exception.InvalidReceiptException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.receipt.entity.Receipt;
import com.expensewise.receipt.repository.ReceiptRepository;
import com.expensewise.storage.StorageService;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reuses the existing StorageService seam (built for the Profile avatar) —
 * no second storage integration. Ownership is always checked through the
 * TRANSACTION, never a receipt id directly: a receipt has no owner column
 * of its own, so every operation resolves "does this transaction belong to
 * the caller" first (findOwnedTransactionOrThrow), matching
 * TransactionService's own 404-not-403 convention.
 */
@Service
public class ReceiptService {

    private static final Duration RECEIPT_URL_TTL = Duration.ofMinutes(15);
    private static final long MAX_RECEIPT_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_RECEIPT_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private static final String PATH_DELIMITER = "/";

    private final TransactionRepository transactionRepository;
    private final ReceiptRepository receiptRepository;
    private final StorageService storageService;

    public ReceiptService(TransactionRepository transactionRepository,
                           ReceiptRepository receiptRepository,
                           StorageService storageService) {
        this.transactionRepository = transactionRepository;
        this.receiptRepository = receiptRepository;
        this.storageService = storageService;
    }

    /**
     * Uploads (or replaces) the one receipt allowed per transaction — the
     * DB's UNIQUE FK on receipts.transaction_id is the source of truth, but
     * this reads the existing row first so a replace can delete the
     * previous storage object instead of orphaning it.
     */
    @Transactional
    public void uploadReceipt(Long userId, Long transactionId, MultipartFile file) {
        validateReceiptFile(file);
        findOwnedTransactionOrThrow(userId, transactionId);

        String newPath = "receipts" + PATH_DELIMITER + userId + PATH_DELIMITER + transactionId + PATH_DELIMITER
                + UUID.randomUUID() + extensionFor(file.getContentType());
        storageService.upload(newPath, readBytes(file), file.getContentType());

        Receipt receipt = receiptRepository.findByTransactionId(transactionId).orElseGet(Receipt::new);
        String previousPath = receipt.getStoragePath();
        receipt.setTransactionId(transactionId);
        receipt.setStoragePath(newPath);
        receipt.setOriginalName(file.getOriginalFilename());
        receipt.setMimeType(file.getContentType());
        receipt.setSizeBytes(file.getSize());
        receiptRepository.save(receipt);

        if (previousPath != null) {
            storageService.delete(previousPath);
        }
    }

    @Transactional
    public void removeReceipt(Long userId, Long transactionId) {
        findOwnedTransactionOrThrow(userId, transactionId);
        receiptRepository.findByTransactionId(transactionId).ifPresent(receipt -> {
            storageService.delete(receipt.getStoragePath());
            receiptRepository.delete(receipt);
        });
    }

    /**
     * Called by TransactionService when a transaction itself is deleted —
     * the receipts row cascades away with the FK automatically, but the
     * object sitting in Supabase Storage does not, so it must be deleted
     * explicitly here or it's orphaned forever. No ownership check: the
     * caller has already verified ownership of the transaction being
     * deleted.
     */
    @Transactional
    public void deleteReceiptForTransaction(Long transactionId) {
        receiptRepository.findByTransactionId(transactionId)
                .ifPresent(receipt -> {
                    storageService.delete(receipt.getStoragePath());
                    receiptRepository.delete(receipt);
                });
    }

    /** A fresh signed URL for the transaction's receipt, or null if it has none. Regenerated on every call. */
    @Transactional(readOnly = true)
    public String findReceiptUrl(Long transactionId) {
        return receiptRepository.findByTransactionId(transactionId)
                .map(receipt -> storageService.generateSignedUrl(receipt.getStoragePath(), RECEIPT_URL_TTL))
                .orElse(null);
    }

    /**
     * Bulk version for a paginated list: one query for which of the given
     * transactions have a receipt at all, then a signed-URL call only for
     * those (never for a transaction with no receipt) — keeps a page load
     * from generating up to page-size signed URLs when most rows have none.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findReceiptUrls(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        List<Receipt> receipts = receiptRepository.findByTransactionIdIn(transactionIds);
        return receipts.stream()
                .collect(Collectors.toMap(Receipt::getTransactionId,
                        receipt -> storageService.generateSignedUrl(receipt.getStoragePath(), RECEIPT_URL_TTL)));
    }

    private void validateReceiptFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidReceiptException("Choose a file to upload");
        }
        if (!ALLOWED_RECEIPT_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidReceiptException("Receipt must be a JPG, PNG, WEBP image, or PDF");
        }
        if (file.getSize() > MAX_RECEIPT_BYTES) {
            throw new InvalidReceiptException("Receipt must be 5 MB or smaller");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    /**
     * Only the caller's own transaction can have its receipt read or
     * mutated — another user's transaction is reported as 404, not 403,
     * matching TransactionService.findOwnedOrThrow.
     */
    private Transaction findOwnedTransactionOrThrow(Long userId, Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!transaction.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Transaction not found");
        }
        return transaction;
    }
}
