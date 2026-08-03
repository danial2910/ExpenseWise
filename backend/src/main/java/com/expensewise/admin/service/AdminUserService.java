package com.expensewise.admin.service;

import com.expensewise.admin.dto.AdminCreateUserRequest;
import com.expensewise.admin.dto.AdminUpdateUserAccessRequest;
import com.expensewise.admin.dto.AdminUpdateUserRequest;
import com.expensewise.admin.dto.AdminUserDetailResponse;
import com.expensewise.auth.service.PasswordResetService;
import com.expensewise.auth.service.RefreshTokenService;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.common.TokenHasher;
import com.expensewise.common.UserStatusCache;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.service.EntitlementService;
import com.expensewise.exception.EmailAlreadyExistsException;
import com.expensewise.exception.ResourceNotFoundException;
import com.expensewise.exception.SelfActionNotAllowedException;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import com.expensewise.user.mapper.UserMapper;
import com.expensewise.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final UserStatusCache userStatusCache;
    private final ActivityLogger activityLogger;
    private final EntitlementService entitlementService;
    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository,
                             UserMapper userMapper,
                             RefreshTokenService refreshTokenService,
                             UserStatusCache userStatusCache,
                             ActivityLogger activityLogger,
                             EntitlementService entitlementService,
                             PasswordResetService passwordResetService,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.userStatusCache = userStatusCache;
        this.activityLogger = activityLogger;
        this.entitlementService = entitlementService;
        this.passwordResetService = passwordResetService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String search, String role, Boolean active, Pageable pageable) {
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        return userRepository.searchAdmin(normalizedSearch, role, active, pageable).map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return userMapper.toResponse(findUserOrThrow(id));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long id) {
        User user = findUserOrThrow(id);
        return new AdminUserDetailResponse(userMapper.toResponse(user), entitlementService.getEntitlements(id));
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

    /**
     * Create User panel has no password field (per the design) — the account
     * starts with an unusable random password hash, and the new user gets a
     * "set your password" email reusing the password_reset_tokens mechanism.
     */
    @Transactional
    public AdminUserDetailResponse createUser(AdminCreateUserRequest request, Long actingAdminId) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(TokenHasher.generateRawToken()));
        userRepository.save(user);

        Set<Feature> enabledFeatures = request.enabledFeatures() != null
                ? request.enabledFeatures()
                : Set.of(Feature.values());
        entitlementService.seedDefaults(user.getId(), enabledFeatures);

        passwordResetService.issueSetPasswordToken(user);
        activityLogger.log(actingAdminId, ActivityAction.ADMIN_USER_CREATED, "USER", user.getId(), null);

        return new AdminUserDetailResponse(userMapper.toResponse(user), entitlementService.getEntitlements(user.getId()));
    }

    /** Edit-user panel: role, active status, and feature entitlements saved atomically. */
    @Transactional
    public AdminUserDetailResponse updateAccess(Long id, AdminUpdateUserAccessRequest request, Long actingAdminId) {
        if (!request.active() && id.equals(actingAdminId)) {
            throw new SelfActionNotAllowedException("An admin cannot disable their own account");
        }

        User user = findUserOrThrow(id);
        user.setRole(request.role());
        user.setActive(request.active());
        userStatusCache.invalidate(id);
        entitlementService.replaceAll(id, request.enabledFeatures());

        if (!request.active()) {
            refreshTokenService.revokeAllForUser(id);
        }
        activityLogger.log(actingAdminId, ActivityAction.ADMIN_USER_ACCESS_UPDATED, "USER", id, null);

        return new AdminUserDetailResponse(userMapper.toResponse(user), entitlementService.getEntitlements(id));
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

    /** Hard delete — cascades to every owned row via the FKs in the schema. */
    @Transactional
    public void deleteUser(Long id, Long actingAdminId) {
        if (id.equals(actingAdminId)) {
            throw new SelfActionNotAllowedException("An admin cannot delete their own account");
        }

        User user = findUserOrThrow(id);
        userRepository.delete(user);
        userStatusCache.invalidate(id);
        activityLogger.log(actingAdminId, ActivityAction.ADMIN_USER_DELETED, "USER", id, null);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
