package com.expensewise.admin.dto;

import java.util.List;

public record AdminDashboardResponse(
        AdminDashboardSummaryResponse summary,
        List<MonthlyCountPoint> signupsOverTime,
        List<MonthlyCountPoint> activityOverTime,
        List<FeatureUsageResponse> featureUsage,
        List<RecentSignupResponse> recentSignups
) {
}
