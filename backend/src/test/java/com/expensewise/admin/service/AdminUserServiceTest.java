package com.expensewise.admin.service;

import com.expensewise.admin.dto.AdminCreateUserRequest;
import com.expensewise.admin.dto.AdminUpdateUserAccessRequest;
import com.expensewise.admin.dto.AdminUserDetailResponse;
import com.expensewise.auth.service.PasswordResetService;
import com.expensewise.auth.service.RefreshTokenService;
import com.expensewise.common.ActivityLogger;
import com.expensewise.common.UserStatusCache;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.service.EntitlementService;
import com.expensewise.exception.EmailAlreadyExistsException;
import com.expensewise.exception.SelfActionNotAllowedException;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import com.expensewise.user.mapper.UserMapper;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserStatusCache userStatusCache;

    @Mock
    private ActivityLogger activityLogger;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, userMapper, refreshTokenService, userStatusCache,
                activityLogger, entitlementService, passwordResetService, passwordEncoder);
        lenient().when(userMapper.toResponse(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                    user.isActive(), user.getCreatedAt(), user.getPhone(), user.getDateOfBirth(), user.getGender(),
                    user.getAddress(), null);
        });
    }

    private User existingUser(Long id, boolean active) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("User " + id);
        user.setRole("USER");
        user.setActive(active);
        return user;
    }

    @Test
    void createUserRejectsADuplicateEmail() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        AdminCreateUserRequest request = new AdminCreateUserRequest("New User", "new@example.com", "USER", null);

        assertThatThrownBy(() -> adminUserService.createUser(request, ADMIN_ID))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserWithNoExplicitFeaturesEnablesAllOfThem() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(OTHER_USER_ID);
            return user;
        });
        when(entitlementService.getEntitlements(OTHER_USER_ID)).thenReturn(allEnabled());

        AdminUserDetailResponse response = adminUserService.createUser(
                new AdminCreateUserRequest("New User", "new@example.com", "USER", null), ADMIN_ID);

        verify(entitlementService).seedDefaults(OTHER_USER_ID, Set.of(Feature.values()));
        verify(passwordResetService).issueSetPasswordToken(any(User.class));
        assertThat(response.user().email()).isEqualTo("new@example.com");
    }

    @Test
    void createUserWithAnExplicitFeatureSetSeedsExactlyThat() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(OTHER_USER_ID);
            return user;
        });
        when(entitlementService.getEntitlements(OTHER_USER_ID)).thenReturn(allEnabled());

        adminUserService.createUser(
                new AdminCreateUserRequest("New User", "new@example.com", "USER", Set.of(Feature.BUDGETS)), ADMIN_ID);

        verify(entitlementService).seedDefaults(OTHER_USER_ID, Set.of(Feature.BUDGETS));
    }

    @Test
    void updateAccessAppliesRoleActiveAndFeaturesAtomically() {
        User target = existingUser(OTHER_USER_ID, true);
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(target));
        when(entitlementService.getEntitlements(OTHER_USER_ID)).thenReturn(allEnabled());

        adminUserService.updateAccess(OTHER_USER_ID,
                new AdminUpdateUserAccessRequest("ADMIN", false, Set.of(Feature.TRANSACTIONS)), ADMIN_ID);

        assertThat(target.getRole()).isEqualTo("ADMIN");
        assertThat(target.isActive()).isFalse();
        verify(entitlementService).replaceAll(OTHER_USER_ID, Set.of(Feature.TRANSACTIONS));
        verify(refreshTokenService).revokeAllForUser(OTHER_USER_ID);
        verify(userStatusCache).invalidate(OTHER_USER_ID);
    }

    @Test
    void anAdminCannotDisableThemselvesViaUpdateAccess() {
        AdminUpdateUserAccessRequest request = new AdminUpdateUserAccessRequest("ADMIN", false, Set.of());

        assertThatThrownBy(() -> adminUserService.updateAccess(ADMIN_ID, request, ADMIN_ID))
                .isInstanceOf(SelfActionNotAllowedException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void anAdminCannotDeleteTheirOwnAccount() {
        assertThatThrownBy(() -> adminUserService.deleteUser(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(SelfActionNotAllowedException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deletingAnotherUserRemovesThemAndLogsTheAction() {
        User target = existingUser(OTHER_USER_ID, true);
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(target));

        adminUserService.deleteUser(OTHER_USER_ID, ADMIN_ID);

        verify(userRepository).delete(target);
        verify(userStatusCache).invalidate(OTHER_USER_ID);
    }

    private Map<Feature, Boolean> allEnabled() {
        Map<Feature, Boolean> map = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            map.put(feature, true);
        }
        return map;
    }
}
