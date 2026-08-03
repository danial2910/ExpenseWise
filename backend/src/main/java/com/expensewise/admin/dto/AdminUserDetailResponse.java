package com.expensewise.admin.dto;

import com.expensewise.entitlement.Feature;
import com.expensewise.user.dto.UserResponse;

import java.util.Map;

/**
 * The list endpoint keeps reusing plain {@link UserResponse} (see
 * DECISIONS.md — a parallel DTO there would be pure duplication), but the
 * single-user detail view now needs entitlements too, which UserResponse
 * has no reason to carry for every other caller (e.g. /users/me).
 */
public record AdminUserDetailResponse(
        UserResponse user,
        Map<Feature, Boolean> enabledFeatures
) {
}
