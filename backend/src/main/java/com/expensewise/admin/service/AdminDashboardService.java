package com.expensewise.admin.service;

import com.expensewise.admin.dto.AdminDashboardResponse;
import com.expensewise.admin.dto.AdminDashboardSummaryResponse;
import com.expensewise.admin.dto.FeatureUsageResponse;
import com.expensewise.admin.dto.MonthlyCountPoint;
import com.expensewise.admin.dto.RecentSignupResponse;
import com.expensewise.admin.mapper.AdminDashboardMapper;
import com.expensewise.budget.repository.BudgetRepository;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository;
import com.expensewise.transaction.repository.TransactionRepository;
import com.expensewise.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * System-wide usage analytics for the Admin Dashboard. Aggregates only —
 * never an individual user's financial details (no amounts, no per-user
 * transaction lists). "This month" and the monthly time series are anchored
 * to Asia/Kuala_Lumpur, matching CLAUDE.md's Time rule and BudgetService's
 * existing precedent.
 */
@Service
public class AdminDashboardService {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserFeatureEntitlementRepository entitlementRepository;
    private final AdminDashboardMapper dashboardMapper;
    private final Clock clock;

    public AdminDashboardService(UserRepository userRepository,
                                  TransactionRepository transactionRepository,
                                  BudgetRepository budgetRepository,
                                  UserFeatureEntitlementRepository entitlementRepository,
                                  AdminDashboardMapper dashboardMapper,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.entitlementRepository = entitlementRepository;
        this.dashboardMapper = dashboardMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(int months, int recentLimit) {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActive(true);
        long disabledUsers = userRepository.countByActive(false);
        long adminUsers = userRepository.countByRole(ADMIN_ROLE);
        long regularUsers = userRepository.countByRole(USER_ROLE);
        long totalTransactions = transactionRepository.count();
        long totalBudgets = budgetRepository.count();

        List<MonthlyCountPoint> signupsOverTime = bucketByMonth(userRepository.findAllCreatedAt(), months);
        List<MonthlyCountPoint> activityOverTime = bucketByMonth(transactionRepository.findAllCreatedAt(), months);

        // The last bucket is always the current Asia/Kuala_Lumpur month —
        // reuses the same bucketing rather than a second "is this month" pass.
        long newUsersThisMonth = signupsOverTime.isEmpty() ? 0 : signupsOverTime.get(signupsOverTime.size() - 1).count();

        AdminDashboardSummaryResponse summary = new AdminDashboardSummaryResponse(
                totalUsers, activeUsers, disabledUsers, newUsersThisMonth, adminUsers, regularUsers,
                totalTransactions, totalBudgets);

        List<FeatureUsageResponse> featureUsage = buildFeatureUsage(regularUsers);

        List<RecentSignupResponse> recentSignups = userRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, recentLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(dashboardMapper::toRecentSignupResponse)
                .toList();

        return new AdminDashboardResponse(summary, signupsOverTime, activityOverTime, featureUsage, recentSignups);
    }

    /**
     * Builds exactly `months` consecutive month buckets ending at the
     * current Asia/Kuala_Lumpur month (oldest first), counting each
     * UTC timestamp against the KL calendar month it falls in.
     */
    private List<MonthlyCountPoint> bucketByMonth(List<Instant> timestamps, int months) {
        LocalDate currentMonth = LocalDate.now(clock.withZone(KL_ZONE)).withDayOfMonth(1);

        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            counts.put(currentMonth.minusMonths(i), 0L);
        }

        for (Instant timestamp : timestamps) {
            LocalDate bucket = timestamp.atZone(KL_ZONE).toLocalDate().withDayOfMonth(1);
            counts.computeIfPresent(bucket, (key, existing) -> existing + 1);
        }

        List<MonthlyCountPoint> points = new ArrayList<>(counts.size());
        counts.forEach((month, count) -> points.add(new MonthlyCountPoint(month, count)));
        return points;
    }

    private List<FeatureUsageResponse> buildFeatureUsage(long regularUsers) {
        Map<Feature, Long> disabledCounts = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            disabledCounts.put(feature, 0L);
        }
        entitlementRepository.countDisabledByFeature()
                .forEach(row -> disabledCounts.put(row.getFeature(), row.getDisabledCount()));

        List<FeatureUsageResponse> result = new ArrayList<>(Feature.values().length);
        for (Feature feature : Feature.values()) {
            // A user with no entitlement row for this feature is still able
            // to use it (EntitlementService.isEnabled defaults to enabled),
            // so "enabled" is derived as regularUsers minus explicit
            // disables — never a raw count of enabled=true rows, which
            // would undercount any account missing a row entirely.
            long enabledCount = Math.max(0, regularUsers - disabledCounts.get(feature));
            int percentage = regularUsers == 0 ? 0 : Math.round(100f * enabledCount / regularUsers);
            result.add(new FeatureUsageResponse(feature, enabledCount, percentage));
        }
        return result;
    }
}
