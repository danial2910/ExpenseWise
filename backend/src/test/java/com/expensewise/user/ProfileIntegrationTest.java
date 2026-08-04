package com.expensewise.user;

import com.expensewise.AbstractIntegrationTest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.common.ApiErrorResponse;
import com.expensewise.storage.StorageService;
import com.expensewise.user.dto.LoginHistoryResponse;
import com.expensewise.user.dto.UpdateProfileRequest;
import com.expensewise.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Profile module end to end against real Postgres. StorageService
 * is @MockBean'd throughout — no real Supabase call is ever made, matching
 * the same pattern AiIntegrationTest uses for AiChatClient.
 */
class ProfileIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private StorageService storageService;

    private record PageResponse<T>(List<T> content) {
    }

    private record Registered(String token, String email, String password) {
    }

    private Registered registerAndGetToken(String label) {
        String email = label + "+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd1";
        ResponseEntity<LoginResponse> response = register(label, email, password);
        return new Registered(response.getBody().accessToken(), email, password);
    }

    private ResponseEntity<UserResponse> getProfile(String token) {
        return restTemplate.exchange(baseUrl("/api/v1/users/me"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), UserResponse.class);
    }

    @Test
    void aNewUsersProfileHasNoOptionalFieldsAndNoAvatar() {
        Registered user = registerAndGetToken("Blank");

        UserResponse profile = getProfile(user.token()).getBody();

        assertThat(profile.phone()).isNull();
        assertThat(profile.dateOfBirth()).isNull();
        assertThat(profile.gender()).isNull();
        assertThat(profile.address()).isNull();
        assertThat(profile.avatarUrl()).isNull();
    }

    @Test
    void updatingTheProfilePersistsTheNewFieldsAndTheEmailStaysReadOnly() {
        Registered user = registerAndGetToken("Editor");

        UpdateProfileRequest request = new UpdateProfileRequest(
                "Editor Updated", "+60 12-345 6789", LocalDate.of(1995, Month.JUNE, 1), "FEMALE", "1 Jalan Test, KL");
        ResponseEntity<UserResponse> response = restTemplate.exchange(baseUrl("/api/v1/users/me"), HttpMethod.PATCH,
                new HttpEntity<>(request, bearerHeaders(user.token())), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse body = response.getBody();
        assertThat(body.fullName()).isEqualTo("Editor Updated");
        assertThat(body.phone()).isEqualTo("+60 12-345 6789");
        assertThat(body.dateOfBirth()).isEqualTo(LocalDate.of(1995, Month.JUNE, 1));
        assertThat(body.gender()).isEqualTo("FEMALE");
        assertThat(body.address()).isEqualTo("1 Jalan Test, KL");
        // Registration normalizes email to lowercase; the label ("Editor") capitalizes
        // the local part, so compare case-insensitively rather than assuming an exact echo.
        assertThat(body.email()).isEqualToIgnoringCase(user.email());
    }

    @Test
    void aDateOfBirthInTheFutureIsRejectedWithACleanValidationError() {
        Registered user = registerAndGetToken("FutureDob");

        UpdateProfileRequest request = new UpdateProfileRequest(
                "Future Dob", null, LocalDate.now().plusDays(1), null, null);
        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(baseUrl("/api/v1/users/me"), HttpMethod.PATCH,
                new HttpEntity<>(request, bearerHeaders(user.token())), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fieldErrors()).containsKey("dateOfBirth");
    }

    @Test
    void uploadingAnAvatarPersistsItAndReturnsAFreshlySignedUrl() {
        Registered user = registerAndGetToken("Avatar");
        when(storageService.generateSignedUrl(anyString(), eq(Duration.ofMinutes(15))))
                .thenReturn("https://signed.example/avatar.jpg");

        ResponseEntity<UserResponse> response = uploadAvatar(user.token(), "avatar.jpg", MediaType.IMAGE_JPEG, new byte[]{1, 2, 3});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().avatarUrl()).isEqualTo("https://signed.example/avatar.jpg");
        verify(storageService).upload(anyString(), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void uploadingANonImageFileIsRejectedCleanly() {
        Registered user = registerAndGetToken("BadAvatar");

        ResponseEntity<ApiErrorResponse> response = restTemplate.exchange(baseUrl("/api/v1/users/me/avatar"),
                HttpMethod.POST, multipartRequest(user.token(), "doc.pdf", MediaType.APPLICATION_PDF, new byte[]{1}),
                ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("avatar");
        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void removingAnAvatarClearsItAndDeletesTheStoredObject() {
        Registered user = registerAndGetToken("RemoveAvatar");
        when(storageService.generateSignedUrl(anyString(), any())).thenReturn("https://signed.example/avatar.jpg");
        uploadAvatar(user.token(), "avatar.png", MediaType.IMAGE_PNG, new byte[]{1, 2, 3});

        ResponseEntity<UserResponse> response = restTemplate.exchange(baseUrl("/api/v1/users/me/avatar"),
                HttpMethod.DELETE, new HttpEntity<>(bearerHeaders(user.token())), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().avatarUrl()).isNull();
        verify(storageService).delete(anyString());
    }

    @Test
    void loginHistoryIsScopedToTheCallingUserOnly() {
        Registered userA = registerAndGetToken("HistoryA");
        Registered userB = registerAndGetToken("HistoryB");
        login(userA.email(), userA.password());
        login(userA.email(), "WrongPassword1");

        ResponseEntity<PageResponse<LoginHistoryResponse>> responseA = restTemplate.exchange(
                baseUrl("/api/v1/users/me/login-history"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userA.token())),
                new ParameterizedTypeReference<PageResponse<LoginHistoryResponse>>() {
                });
        ResponseEntity<PageResponse<LoginHistoryResponse>> responseB = restTemplate.exchange(
                baseUrl("/api/v1/users/me/login-history"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userB.token())),
                new ParameterizedTypeReference<PageResponse<LoginHistoryResponse>>() {
                });

        assertThat(responseA.getBody().content()).extracting(LoginHistoryResponse::status)
                .contains("Success", "Failed");
        assertThat(responseB.getBody().content()).isEmpty();
    }

    @Test
    void logoutOthersRevokesOtherSessionsButKeepsTheCurrentOne() {
        Registered user = registerAndGetToken("MultiSession");
        ResponseEntity<LoginResponse> session1 = login(user.email(), user.password());
        ResponseEntity<LoginResponse> session2 = login(user.email(), user.password());
        String session1Cookie = extractRefreshCookie(session1);
        String session2Cookie = extractRefreshCookie(session2);
        String session2AccessToken = session2.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session2AccessToken);
        headers.add(HttpHeaders.COOKIE, session2Cookie);
        ResponseEntity<Void> logoutOthersResponse = restTemplate.exchange(baseUrl("/api/v1/auth/logout-others"),
                HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logoutOthersResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // session2 first: reusing session1's already-revoked cookie next triggers
        // RefreshTokenService's own reuse-detection, which revokes the whole
        // family (including session2) as a compromise signal — checking
        // session2 afterward would see that side effect, not this feature.
        ResponseEntity<Void> session2Refresh = restTemplate.exchange(baseUrl("/api/v1/auth/refresh"),
                HttpMethod.POST, withCookie(session2Cookie), Void.class);
        assertThat(session2Refresh.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiErrorResponse> session1Refresh = restTemplate.exchange(baseUrl("/api/v1/auth/refresh"),
                HttpMethod.POST, withCookie(session1Cookie), ApiErrorResponse.class);
        assertThat(session1Refresh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void logoutOthersWithoutAuthenticationIsRejected() {
        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/logout-others"), null, ApiErrorResponse.class);

        // @PreAuthorize failures always surface as 403 in this app (see
        // GlobalExceptionHandler.handleAuthorizationDenied) — including "no
        // principal at all", not just "authenticated but wrong role".
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<UserResponse> uploadAvatar(String token, String filename, MediaType contentType, byte[] bytes) {
        return restTemplate.exchange(baseUrl("/api/v1/users/me/avatar"), HttpMethod.POST,
                multipartRequest(token, filename, contentType, bytes), UserResponse.class);
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
}
