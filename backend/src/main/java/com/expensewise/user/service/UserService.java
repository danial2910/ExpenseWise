package com.expensewise.user.service;

import com.expensewise.auth.service.PasswordPolicyValidator;
import com.expensewise.auth.service.RefreshTokenService;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.common.repository.ActivityLogRepository;
import com.expensewise.exception.InvalidAvatarException;
import com.expensewise.exception.InvalidCredentialsException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.exception.WeakPasswordException;
import com.expensewise.storage.StorageService;
import com.expensewise.user.dto.ChangePasswordRequest;
import com.expensewise.user.dto.LoginHistoryResponse;
import com.expensewise.user.dto.UpdateProfileRequest;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import com.expensewise.user.mapper.UserMapper;
import com.expensewise.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private static final Duration AVATAR_URL_TTL = Duration.ofMinutes(15);
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> LOGIN_ACTIONS = List.of(ActivityAction.LOGIN_SUCCESS, ActivityAction.LOGIN_FAILED);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final RefreshTokenService refreshTokenService;
    private final ActivityLogger activityLogger;
    private final ActivityLogRepository activityLogRepository;
    private final StorageService storageService;

    public UserService(UserRepository userRepository,
                        UserMapper userMapper,
                        PasswordEncoder passwordEncoder,
                        PasswordPolicyValidator passwordPolicyValidator,
                        RefreshTokenService refreshTokenService,
                        ActivityLogger activityLogger,
                        ActivityLogRepository activityLogRepository,
                        StorageService storageService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.refreshTokenService = refreshTokenService;
        this.activityLogger = activityLogger;
        this.activityLogRepository = activityLogRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return toResponseWithAvatar(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setDateOfBirth(request.dateOfBirth());
        user.setGender(blankToNull(request.gender()));
        user.setAddress(blankToNull(request.address()));
        activityLogger.log(userId, ActivityAction.PROFILE_UPDATED);
        return toResponseWithAvatar(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        if (!passwordPolicyValidator.isValid(request.newPassword())) {
            throw new WeakPasswordException(
                    "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, and a number");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(userId);
        activityLogger.log(userId, ActivityAction.PASSWORD_CHANGED);
    }

    @Transactional
    public UserResponse uploadAvatar(Long userId, MultipartFile file) {
        validateAvatarFile(file);
        User user = findUserOrThrow(userId);

        String newPath = "avatars/" + userId + "/" + UUID.randomUUID() + extensionFor(file.getContentType());
        storageService.upload(newPath, readBytes(file), file.getContentType());

        String previousPath = user.getAvatarPath();
        user.setAvatarPath(newPath);
        if (previousPath != null) {
            storageService.delete(previousPath);
        }
        activityLogger.log(userId, ActivityAction.AVATAR_UPDATED);
        return toResponseWithAvatar(user);
    }

    @Transactional
    public UserResponse removeAvatar(Long userId) {
        User user = findUserOrThrow(userId);
        if (user.getAvatarPath() != null) {
            storageService.delete(user.getAvatarPath());
            user.setAvatarPath(null);
            activityLogger.log(userId, ActivityAction.AVATAR_REMOVED);
        }
        return toResponseWithAvatar(user);
    }

    @Transactional(readOnly = true)
    public Page<LoginHistoryResponse> getLoginHistory(Long userId, Pageable pageable) {
        return activityLogRepository.findByUserIdAndActionInOrderByCreatedAtDesc(userId, LOGIN_ACTIONS, pageable)
                .map(userMapper::toLoginHistoryResponse);
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarException("Choose an image to upload");
        }
        if (!ALLOWED_AVATAR_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidAvatarException("Avatar must be a JPG, PNG, or WEBP image");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new InvalidAvatarException("Avatar must be 2 MB or smaller");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
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

    private UserResponse toResponseWithAvatar(User user) {
        String avatarUrl = user.getAvatarPath() != null
                ? storageService.generateSignedUrl(user.getAvatarPath(), AVATAR_URL_TTL)
                : null;
        return userMapper.toResponse(user, avatarUrl);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
