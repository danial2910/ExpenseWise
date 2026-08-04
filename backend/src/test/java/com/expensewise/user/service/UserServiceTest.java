package com.expensewise.user.service;

import com.expensewise.auth.service.PasswordPolicyValidator;
import com.expensewise.auth.service.RefreshTokenService;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.common.entity.ActivityLog;
import com.expensewise.common.repository.ActivityLogRepository;
import com.expensewise.exception.InvalidAvatarException;
import com.expensewise.storage.StorageService;
import com.expensewise.user.dto.LoginHistoryResponse;
import com.expensewise.user.dto.UpdateProfileRequest;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import com.expensewise.user.mapper.UserMapper;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private ActivityLogger activityLogger;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private StorageService storageService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, passwordEncoder, passwordPolicyValidator,
                refreshTokenService, activityLogger, activityLogRepository, storageService);
        lenient().when(userMapper.toResponse(any(User.class), any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            String avatarUrl = invocation.getArgument(1);
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                    user.isActive(), user.getCreatedAt(), user.getPhone(), user.getDateOfBirth(),
                    user.getGender(), user.getAddress(), avatarUrl);
        });
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("sarah@example.com");
        user.setFullName("Sarah Lim");
        user.setPasswordHash("hash");
        return user;
    }

    @Test
    void updateProfilePersistsNewFieldsAndLogsActivity() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateProfile(USER_ID,
                new UpdateProfileRequest("Sarah Lim Updated", "+60 12-345 6789", LocalDate.of(1995, Month.JUNE, 1),
                        "FEMALE", "1 Jalan Test, KL"));

        assertThat(user.getFullName()).isEqualTo("Sarah Lim Updated");
        assertThat(user.getPhone()).isEqualTo("+60 12-345 6789");
        assertThat(user.getDateOfBirth()).isEqualTo(LocalDate.of(1995, Month.JUNE, 1));
        assertThat(user.getGender()).isEqualTo("FEMALE");
        assertThat(user.getAddress()).isEqualTo("1 Jalan Test, KL");
        assertThat(response.fullName()).isEqualTo("Sarah Lim Updated");
        verify(activityLogger).log(USER_ID, ActivityAction.PROFILE_UPDATED);
    }

    @Test
    void updateProfileTreatsBlankOptionalFieldsAsNull() {
        User user = user();
        user.setPhone("old-phone");
        user.setGender("MALE");
        user.setAddress("old address");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.updateProfile(USER_ID, new UpdateProfileRequest("Sarah Lim", "", null, "", ""));

        assertThat(user.getPhone()).isNull();
        assertThat(user.getGender()).isNull();
        assertThat(user.getAddress()).isNull();
        assertThat(user.getDateOfBirth()).isNull();
    }

    @Test
    void uploadAvatarPersistsThePathAndReturnsAFreshlySignedUrl() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(storageService.generateSignedUrl(any(), eq(Duration.ofMinutes(15)))).thenReturn("https://signed.example/avatar.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        UserResponse response = userService.uploadAvatar(USER_ID, file);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(pathCaptor.capture(), any(byte[].class), eq("image/jpeg"));
        assertThat(pathCaptor.getValue()).startsWith("avatars/" + USER_ID + "/").endsWith(".jpg");
        assertThat(user.getAvatarPath()).isEqualTo(pathCaptor.getValue());
        assertThat(response.avatarUrl()).isEqualTo("https://signed.example/avatar.jpg");
        verify(activityLogger).log(USER_ID, ActivityAction.AVATAR_UPDATED);
    }

    @Test
    void uploadAvatarDeletesThePreviousObjectOnReplace() {
        User user = user();
        user.setAvatarPath("avatars/1/old.jpg");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(storageService.generateSignedUrl(any(), any())).thenReturn("https://signed.example/new.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "new.png", "image/png", new byte[]{1, 2, 3});

        userService.uploadAvatar(USER_ID, file);

        verify(storageService).delete("avatars/1/old.jpg");
    }

    @Test
    void uploadAvatarRejectsANonImageContentType() {
        // Validation happens before the user is looked up, so no repository stub is needed.
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> userService.uploadAvatar(USER_ID, file))
                .isInstanceOf(InvalidAvatarException.class);

        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void uploadAvatarRejectsAFileOverTheSizeLimit() {
        byte[] oversized = new byte[(2 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> userService.uploadAvatar(USER_ID, file))
                .isInstanceOf(InvalidAvatarException.class);

        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void removeAvatarClearsThePathAndDeletesTheObject() {
        User user = user();
        user.setAvatarPath("avatars/1/current.jpg");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserResponse response = userService.removeAvatar(USER_ID);

        verify(storageService).delete("avatars/1/current.jpg");
        assertThat(user.getAvatarPath()).isNull();
        assertThat(response.avatarUrl()).isNull();
        verify(activityLogger).log(USER_ID, ActivityAction.AVATAR_REMOVED);
    }

    @Test
    void removeAvatarWhenThereIsNoAvatarIsANoOp() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        userService.removeAvatar(USER_ID);

        verify(storageService, never()).delete(any());
        verify(activityLogger, never()).log(USER_ID, ActivityAction.AVATAR_REMOVED);
    }

    @Test
    void getLoginHistoryScopesTheQueryToTheCallingUsersLoginActions() {
        ActivityLog log = new ActivityLog();
        log.setUserId(USER_ID);
        log.setAction(ActivityAction.LOGIN_SUCCESS);
        Pageable pageable = Pageable.ofSize(20);
        when(activityLogRepository.findByUserIdAndActionInOrderByCreatedAtDesc(eq(USER_ID), anyList(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(userMapper.toLoginHistoryResponse(log))
                .thenReturn(new LoginHistoryResponse(null, null, "Success"));

        Page<LoginHistoryResponse> result = userService.getLoginHistory(USER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<List<String>> actionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(activityLogRepository).findByUserIdAndActionInOrderByCreatedAtDesc(eq(USER_ID), actionsCaptor.capture(), eq(pageable));
        assertThat(actionsCaptor.getValue()).containsExactlyInAnyOrder(ActivityAction.LOGIN_SUCCESS, ActivityAction.LOGIN_FAILED);
    }
}
