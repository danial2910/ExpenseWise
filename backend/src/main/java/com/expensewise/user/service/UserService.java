package com.expensewise.user.service;

import com.expensewise.auth.service.PasswordPolicyValidator;
import com.expensewise.auth.service.RefreshTokenService;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.exception.InvalidCredentialsException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.exception.WeakPasswordException;
import com.expensewise.user.dto.ChangePasswordRequest;
import com.expensewise.user.dto.UpdateProfileRequest;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import com.expensewise.user.mapper.UserMapper;
import com.expensewise.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final RefreshTokenService refreshTokenService;
    private final ActivityLogger activityLogger;

    public UserService(UserRepository userRepository,
                        UserMapper userMapper,
                        PasswordEncoder passwordEncoder,
                        PasswordPolicyValidator passwordPolicyValidator,
                        RefreshTokenService refreshTokenService,
                        ActivityLogger activityLogger) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.refreshTokenService = refreshTokenService;
        this.activityLogger = activityLogger;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userMapper.toResponse(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        user.setFullName(request.fullName().trim());
        return userMapper.toResponse(user);
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

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
