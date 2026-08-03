package com.expensewise.admin.dto;

import com.expensewise.entitlement.Feature;

/**
 * percentage is of {@code regularUsers} (USER-role accounts only) — ADMIN
 * accounts never get entitlement rows (see EntitlementService), so they'd
 * only dilute the "% of users active" figure the design shows.
 */
public record FeatureUsageResponse(
        Feature feature,
        long enabledCount,
        int percentage
) {
}
