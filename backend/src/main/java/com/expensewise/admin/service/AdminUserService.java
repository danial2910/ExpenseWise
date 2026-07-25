package com.expensewise.admin.service;

import com.expensewise.admin.dto.AdminUpdateUserRequest;
import com.expensewise.auth.service.RefreshTokenService;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.common.UserStatusCache;
import com.expensewise.exception.EmailAlreadyExistsException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.exception.SelfActionNotAllowedException;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import com.expensewise.user.mapper.UserMapper;
import com.expensewise.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final UserStatusCache userStatusCache;
    private final ActivityLogger activityLogger;

    public AdminUserService(UserRepository userRepository,
                             UserMapper userMapper,
                             RefreshTokenService refreshTokenService,
                             UserStatusCache userStatusCache,
                             ActivityLogger activityLogger) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.userStatusCache = userStatusCache;
        this.activityLogger = activityLogger;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String search, Pageable pageable) {
        Page<User> page = StringUtils.hasText(search)
                ? userRepository.search(search.trim(), pageable)
                : userRepository.findAll(pageable);
        return page.map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return userMapper.toResponse(findUserOrThrow(id));
    }

    @Transactional
    public UserResponse updateUser(Long id, AdminUpdateUserRequest request) {
        User user = findUserOrThrow(id);

        String email = request.email().trim().toLowerCase();
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse setStatus(Long id, boolean active, Long actingAdminId) {
        if (!active && id.equals(actingAdminId)) {
            throw new SelfActionNotAllowedException("An admin cannot disable their own account");
        }

        User user = findUserOrThrow(id);
        user.setActive(active);
        userStatusCache.invalidate(id);

        if (!active) {
            refreshTokenService.revokeAllForUser(id);
            activityLogger.log(actingAdminId, ActivityAction.ADMIN_USER_DISABLED, "USER", id, null);
        } else {
            activityLogger.log(actingAdminId, ActivityAction.ADMIN_USER_ENABLED, "USER", id, null);
        }

        return userMapper.toResponse(user);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
