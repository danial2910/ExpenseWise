package com.expensewise.receipt.service;

import com.expensewise.exception.InvalidReceiptException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.receipt.entity.Receipt;
import com.expensewise.receipt.repository.ReceiptRepository;
import com.expensewise.storage.StorageService;
import com.expensewise.transaction.entity.Transaction;
import com.expensewise.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long TRANSACTION_ID = 100L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private StorageService storageService;

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(transactionRepository, receiptRepository, storageService);
    }

    private Transaction ownTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setUserId(USER_ID);
        return transaction;
    }

    private Transaction othersTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(200L);
        transaction.setUserId(OTHER_USER_ID);
        return transaction;
    }

    // --- ownership ---

    @Test
    void uploadingAReceiptOnAnotherUsersTransactionIsRejectedAs404() {
        when(transactionRepository.findById(200L)).thenReturn(Optional.of(othersTransaction()));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> receiptService.uploadReceipt(USER_ID, 200L, file))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void removingAReceiptOnAnotherUsersTransactionIsRejectedAs404() {
        when(transactionRepository.findById(200L)).thenReturn(Optional.of(othersTransaction()));

        assertThatThrownBy(() -> receiptService.removeReceipt(USER_ID, 200L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storageService, never()).delete(anyString());
    }

    @Test
    void uploadingAReceiptOnAMissingTransactionIsRejectedAs404() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> receiptService.uploadReceipt(USER_ID, 999L, file))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- validation ---

    @Test
    void rejectsAFileTypeThatIsNotAnAllowedImageOrPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> receiptService.uploadReceipt(USER_ID, TRANSACTION_ID, file))
                .isInstanceOf(InvalidReceiptException.class);

        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        byte[] oversized = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> receiptService.uploadReceipt(USER_ID, TRANSACTION_ID, file))
                .isInstanceOf(InvalidReceiptException.class);

        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void rejectsAnEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> receiptService.uploadReceipt(USER_ID, TRANSACTION_ID, file))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void acceptsAPdfReceipt() {
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(ownTransaction()));
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", new byte[]{1, 2});

        receiptService.uploadReceipt(USER_ID, TRANSACTION_ID, file);

        verify(storageService).upload(anyString(), any(byte[].class), eq("application/pdf"));
    }

    // --- upload persists metadata / one-per-transaction / replace ---

    @Test
    void uploadingAReceiptPersistsItsMetadata() {
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(ownTransaction()));
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "grocery-receipt.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        receiptService.uploadReceipt(USER_ID, TRANSACTION_ID, file);

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(captor.capture());
        Receipt saved = captor.getValue();
        assertThat(saved.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.getOriginalName()).isEqualTo("grocery-receipt.jpg");
        assertThat(saved.getMimeType()).isEqualTo("image/jpeg");
        assertThat(saved.getSizeBytes()).isEqualTo(4L);
        assertThat(saved.getStoragePath()).startsWith("receipts/" + USER_ID + "/" + TRANSACTION_ID + "/");
    }

    @Test
    void replacingAnExistingReceiptDeletesThePreviousStorageObject() {
        Receipt existing = new Receipt();
        existing.setId(5L);
        existing.setTransactionId(TRANSACTION_ID);
        existing.setStoragePath("receipts/1/100/old.jpg");
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(ownTransaction()));
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.of(existing));
        MockMultipartFile file = new MockMultipartFile("file", "new-receipt.png", "image/png", new byte[]{9, 9});

        receiptService.uploadReceipt(USER_ID, TRANSACTION_ID, file);

        verify(storageService).upload(anyString(), any(byte[].class), eq("image/png"));
        verify(storageService).delete("receipts/1/100/old.jpg");
        // The same row is reused (one receipt per transaction), not a second insert.
        verify(receiptRepository, times(1)).save(existing);
    }

    // --- remove ---

    @Test
    void removingAReceiptDeletesTheStorageObjectAndTheRow() {
        Receipt existing = new Receipt();
        existing.setId(5L);
        existing.setTransactionId(TRANSACTION_ID);
        existing.setStoragePath("receipts/1/100/file.jpg");
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(ownTransaction()));
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.of(existing));

        receiptService.removeReceipt(USER_ID, TRANSACTION_ID);

        verify(storageService).delete("receipts/1/100/file.jpg");
        verify(receiptRepository).delete(existing);
    }

    @Test
    void removingANonExistentReceiptIsANoOp() {
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(ownTransaction()));
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.empty());

        receiptService.removeReceipt(USER_ID, TRANSACTION_ID);

        verify(storageService, never()).delete(anyString());
        verify(receiptRepository, never()).delete(any());
    }

    // --- delete-for-transaction (cascade cleanup) ---

    @Test
    void deleteReceiptForTransactionCleansUpStorageWhenAReceiptExists() {
        Receipt existing = new Receipt();
        existing.setId(5L);
        existing.setTransactionId(TRANSACTION_ID);
        existing.setStoragePath("receipts/1/100/file.jpg");
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.of(existing));

        receiptService.deleteReceiptForTransaction(TRANSACTION_ID);

        verify(storageService).delete("receipts/1/100/file.jpg");
        verify(receiptRepository).delete(existing);
    }

    @Test
    void deleteReceiptForTransactionIsANoOpWhenThereIsNoReceipt() {
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.empty());

        receiptService.deleteReceiptForTransaction(TRANSACTION_ID);

        verify(storageService, never()).delete(anyString());
    }

    // --- signed URL generation ---

    @Test
    void findReceiptUrlReturnsNullWhenThereIsNoReceipt() {
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThat(receiptService.findReceiptUrl(TRANSACTION_ID)).isNull();
        verify(storageService, never()).generateSignedUrl(anyString(), any());
    }

    @Test
    void findReceiptUrlReturnsAFreshlySignedUrlWhenAReceiptExists() {
        Receipt existing = new Receipt();
        existing.setStoragePath("receipts/1/100/file.jpg");
        when(receiptRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.of(existing));
        when(storageService.generateSignedUrl(eq("receipts/1/100/file.jpg"), eq(Duration.ofMinutes(15))))
                .thenReturn("https://signed.example/file.jpg");

        assertThat(receiptService.findReceiptUrl(TRANSACTION_ID)).isEqualTo("https://signed.example/file.jpg");
    }

    @Test
    void findReceiptUrlsOnlyGeneratesUrlsForTransactionsThatActuallyHaveAReceipt() {
        Receipt existing = new Receipt();
        existing.setTransactionId(TRANSACTION_ID);
        existing.setStoragePath("receipts/1/100/file.jpg");
        when(receiptRepository.findByTransactionIdIn(List.of(TRANSACTION_ID, 101L))).thenReturn(List.of(existing));
        when(storageService.generateSignedUrl(eq("receipts/1/100/file.jpg"), any()))
                .thenReturn("https://signed.example/file.jpg");

        Map<Long, String> urls = receiptService.findReceiptUrls(List.of(TRANSACTION_ID, 101L));

        assertThat(urls).containsExactly(Map.entry(TRANSACTION_ID, "https://signed.example/file.jpg"));
    }

    @Test
    void findReceiptUrlsWithNoTransactionIdsSkipsTheQueryEntirely() {
        Map<Long, String> urls = receiptService.findReceiptUrls(List.of());

        assertThat(urls).isEmpty();
        verify(receiptRepository, never()).findByTransactionIdIn(any());
    }
}
