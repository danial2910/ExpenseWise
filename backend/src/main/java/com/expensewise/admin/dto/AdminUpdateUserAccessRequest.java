package com.expensewise.admin.dto;

import com.expensewise.entitlement.Feature;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Edit-user panel: role, active status, and feature entitlements are all
 * saved atomically in one request — matches the design's single "Save
 * changes" button acting on all three at once.
 */
public record AdminUpdateUserAccessRequest(
        @NotNull(message = "Role is required")
        @Pattern(regexp = "USER|ADMIN", message = "Role must be USER or ADMIN")
        String role,

        @NotNull(message = "active is required")
        Boolean active,

        @NotNull(message = "enabledFeatures is required")
        Set<Feature> enabledFeatures
) {
}
