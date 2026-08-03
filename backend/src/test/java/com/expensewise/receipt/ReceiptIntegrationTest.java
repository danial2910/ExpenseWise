package com.expensewise.receipt;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.storage.StorageService;
import com.expensewise.transaction.dto.TransactionRequest;
import com.expensewise.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the receipt feature end to end against real Postgres. StorageService
 * is @MockBean'd throughout — no real Supabase call is ever made, matching
 * ProfileIntegrationTest's pattern for the avatar upload.
 */
class ReceiptIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private StorageService storageService;

    private record PageResponse<T>(List<T> content) {
    }

    private record Registered(String token, Long userId) {
    }

    private Registered registerAndGetToken(String label) {
        String email = label + "+" + System.nanoTime() + "@example.com";
        ResponseEntity<LoginResponse> response = register(label, email, "Passw0rd1");
        return new Registered(response.getBody().accessToken(), response.getBody().user().id());
    }

    private Long systemCategoryId(String token, String name, String type) {
        ResponseEntity<PageResponse<CategoryResponse>> response = restTemplate.exchange(
                baseUrl("/api/v1/categories?size=100&type=" + type), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                new ParameterizedTypeReference<PageResponse<CategoryResponse>>() {
                });
        return response.getBody().content().stream()
                .filter(c -> c.system() && c.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private Long createTransaction(String token, Long categoryId) {
        ResponseEntity<TransactionResponse> response = restTemplate.exchange(baseUrl("/api/v1/transactions"),
                HttpMethod.POST,
                new HttpEntity<>(new TransactionRequest("EXPENSE", new BigDecimal("15.00"), categoryId, LocalDate.now(), "Lunch"),
                        bearerHeaders(token)),
                TransactionResponse.class);
        return response.getBody().id();
    }

    private ResponseEntity<TransactionResponse> uploadReceipt(String token, Long transactionId, String filename,
                                                                MediaType contentType, byte[] bytes) {
        return restTemplate.exchange(baseUrl("/api/v1/transactions/" + transactionId + "/receipt"), HttpMethod.POST,
                multipartRequest(token, filename, contentType, bytes), TransactionResponse.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartRequest(String token, String filename,
                                                                          MediaType contentType, byte[] bytes) {
        var namedResource = new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(contentType);
        HttpEntity<org.springframework.core.io.ByteArrayResource> filePart = new HttpEntity<>(namedResource, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void uploadingAReceiptPersistsItAndReturnsAFreshlySignedUrlOnTheTransaction() {
        Registered user = registerAndGetToken("ReceiptUploader");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(user.token(), foodCategoryId);
        when(storageService.generateSignedUrl(anyString(), eq(Duration.ofMinutes(15))))
                .thenReturn("https://signed.example/receipt.jpg");

        ResponseEntity<TransactionResponse> response =
                uploadReceipt(user.token(), transactionId, "receipt.jpg", MediaType.IMAGE_JPEG, new byte[]{1, 2, 3});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().receiptUrl()).isEqualTo("https://signed.example/receipt.jpg");
        verify(storageService).upload(anyString(), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void gettingTheTransactionAfterUploadStillReturnsTheReceiptUrl() {
        Registered user = registerAndGetToken("ReceiptReader");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(user.token(), foodCategoryId);
        when(storageService.generateSignedUrl(anyString(), any())).thenReturn("https://signed.example/r.jpg");
        uploadReceipt(user.token(), transactionId, "r.jpg", MediaType.IMAGE_JPEG, new byte[]{1});

        ResponseEntity<TransactionResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(user.token())), TransactionResponse.class);

        assertThat(response.getBody().receiptUrl()).isEqualTo("https://signed.example/r.jpg");
    }

    @Test
    void replacingAReceiptDeletesThePreviousStorageObject() {
        Registered user = registerAndGetToken("ReceiptReplacer");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(user.token(), foodCategoryId);
        when(storageService.generateSignedUrl(anyString(), any())).thenReturn("https://signed.example/r.jpg");
        uploadReceipt(user.token(), transactionId, "first.jpg", MediaType.IMAGE_JPEG, new byte[]{1});

        uploadReceipt(user.token(), transactionId, "second.png", MediaType.IMAGE_PNG, new byte[]{2, 2});

        verify(storageService).delete(anyString());
        verify(storageService, org.mockito.Mockito.times(2)).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void removingAReceiptClearsItAndDeletesTheStoredObject() {
        Registered user = registerAndGetToken("ReceiptRemover");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(user.token(), foodCategoryId);
        when(storageService.generateSignedUrl(anyString(), any())).thenReturn("https://signed.example/r.jpg");
        uploadReceipt(user.token(), transactionId, "r.jpg", MediaType.IMAGE_JPEG, new byte[]{1});

        ResponseEntity<TransactionResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId + "/receipt"), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(user.token())), TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().receiptUrl()).isNull();
        verify(storageService).delete(anyString());
    }

    @Test
    void uploadingANonAllowedFileTypeIsRejectedCleanly() {
        Registered user = registerAndGetToken("BadReceipt");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(user.token(), foodCategoryId);

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId + "/receipt"), HttpMethod.POST,
                multipartRequest(user.token(), "malware.exe", MediaType.APPLICATION_OCTET_STREAM, new byte[]{1}),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("file");
        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void deletingATransactionRemovesItsReceiptFromStorageToo() {
        Registered user = registerAndGetToken("ReceiptCascade");
        Long foodCategoryId = systemCategoryId(user.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(user.token(), foodCategoryId);
        when(storageService.generateSignedUrl(anyString(), any())).thenReturn("https://signed.example/r.jpg");
        uploadReceipt(user.token(), transactionId, "r.jpg", MediaType.IMAGE_JPEG, new byte[]{1});

        restTemplate.exchange(baseUrl("/api/v1/transactions/" + transactionId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(user.token())), Void.class);

        verify(storageService).delete(anyString());
    }

    // --- security: user A cannot touch user B's transaction's receipt ---

    @Test
    void userACannotUploadViewOrDeleteAReceiptOnUserBsTransaction() {
        Registered userA = registerAndGetToken("ReceiptTrespasser");
        Registered userB = registerAndGetToken("ReceiptOwner");
        Long foodCategoryId = systemCategoryId(userB.token(), "Food", "EXPENSE");
        Long transactionId = createTransaction(userB.token(), foodCategoryId);

        ResponseEntity<ApiErrorResponse> uploadResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId + "/receipt"), HttpMethod.POST,
                multipartRequest(userA.token(), "r.jpg", MediaType.IMAGE_JPEG, new byte[]{1}), ApiErrorResponse.class);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiErrorResponse> deleteResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId + "/receipt"), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Reading userB's transaction as userA is a plain ownership 404 too — no receiptUrl to leak.
        ResponseEntity<ApiErrorResponse> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/transactions/" + transactionId), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userA.token())), ApiErrorResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
    }
}
