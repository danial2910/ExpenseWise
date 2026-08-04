package com.expensewise.admin.service;

import com.expensewise.admin.dto.AdminDashboardResponse;
import com.expensewise.admin.dto.MonthlyCountPoint;
import com.expensewise.admin.mapper.AdminDashboardMapper;
import com.expensewise.budget.repository.BudgetRepository;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository.FeatureDisabledCount;
import com.expensewise.transaction.repository.TransactionRepository;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    // 2026-07-15 noon UTC is still 2026-07-15 evening in Asia/Kuala_Lumpur
    // (UTC+8) — an unambiguous "current month" for both zones, so bucket
    // math isn't accidentally right for the wrong reason.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private UserFeatureEntitlementRepository entitlementRepository;

    @Mock
    private AdminDashboardMapper dashboardMapper;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(userRepository, transactionRepository, budgetRepository,
                entitlementRepository, dashboardMapper, FIXED_CLOCK);
        lenient().when(userRepository.count()).thenReturn(0L);
        lenient().when(userRepository.countByActive(true)).thenReturn(0L);
        lenient().when(userRepository.countByActive(false)).thenReturn(0L);
        lenient().when(userRepository.countByRole(any())).thenReturn(0L);
        lenient().when(userRepository.findAllCreatedAt()).thenReturn(List.of());
        lenient().when(transactionRepository.count()).thenReturn(0L);
        lenient().when(transactionRepository.findAllCreatedAt()).thenReturn(List.of());
        lenient().when(budgetRepository.count()).thenReturn(0L);
        lenient().when(entitlementRepository.countDisabledByFeature()).thenReturn(List.of());
        lenient().when(userRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    private FeatureDisabledCount disabledCount(Feature feature, long count) {
        return new FeatureDisabledCount() {
            @Override
            public Feature getFeature() {
                return feature;
            }

            @Override
            public long getDisabledCount() {
                return count;
            }
        };
    }

    @Test
    void summaryReflectsBasicCounts() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByActive(true)).thenReturn(8L);
        when(userRepository.countByActive(false)).thenReturn(2L);
        when(userRepository.countByRole("ADMIN")).thenReturn(1L);
        when(userRepository.countByRole("USER")).thenReturn(9L);
        when(transactionRepository.count()).thenReturn(120L);
        when(budgetRepository.count()).thenReturn(15L);

        AdminDashboardResponse response = service.getDashboard(6, 5);

        assertThat(response.summary().totalUsers()).isEqualTo(10);
        assertThat(response.summary().activeUsers()).isEqualTo(8);
        assertThat(response.summary().disabledUsers()).isEqualTo(2);
        assertThat(response.summary().adminUsers()).isEqualTo(1);
        assertThat(response.summary().regularUsers()).isEqualTo(9);
        assertThat(response.summary().totalTransactions()).isEqualTo(120);
        assertThat(response.summary().totalBudgets()).isEqualTo(15);
    }

    @Test
    void newUsersThisMonthCountsOnlyTimestampsInTheCurrentKlMonth() {
        // Fixed clock is 2026-07-15T12:00:00Z. 2026-06-30T23:00:00Z is
        // already 2026-07-01T07:00 in Kuala Lumpur (UTC+8) — must count as
        // July, proving the boundary is computed in KL, not UTC.
        when(userRepository.findAllCreatedAt()).thenReturn(List.of(
                Instant.parse("2026-06-30T23:00:00Z"), // July in KL
                Instant.parse("2026-07-14T10:00:00Z"), // July in KL
                Instant.parse("2026-06-15T10:00:00Z")  // June in both zones
        ));

        AdminDashboardResponse response = service.getDashboard(6, 5);

        assertThat(response.summary().newUsersThisMonth()).isEqualTo(2);
    }

    @Test
    void signupsOverTimeReturnsExactlyTheRequestedNumberOfConsecutiveMonthsOldestFirst() {
        when(userRepository.findAllCreatedAt()).thenReturn(List.of(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z")
        ));

        List<MonthlyCountPoint> signups = service.getDashboard(3, 5).signupsOverTime();

        assertThat(signups).extracting(MonthlyCountPoint::month).containsExactly(
                LocalDate.of(2026, Month.MAY, 1), LocalDate.of(2026, Month.JUNE, 1), LocalDate.of(2026, Month.JULY, 1));
        assertThat(signups).extracting(MonthlyCountPoint::count).containsExactly(1L, 0L, 1L);
    }

    @Test
    void activityOverTimeBucketsTransactionCreationTimestampsTheSameWay() {
        when(transactionRepository.findAllCreatedAt()).thenReturn(List.of(
                Instant.parse("2026-07-10T00:00:00Z"),
                Instant.parse("2026-07-11T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        ));

        List<MonthlyCountPoint> activity = service.getDashboard(2, 5).activityOverTime();

        assertThat(activity).extracting(MonthlyCountPoint::month).containsExactly(
                LocalDate.of(2026, Month.JUNE, 1), LocalDate.of(2026, Month.JULY, 1));
        assertThat(activity).extracting(MonthlyCountPoint::count).containsExactly(1L, 2L);
    }

    @Test
    void featureUsagePercentageIsOfRegularUsersOnlyAndCoversEveryFeature() {
        when(userRepository.countByRole("USER")).thenReturn(4L);
        when(entitlementRepository.countDisabledByFeature()).thenReturn(List.of(
                disabledCount(Feature.BUDGETS, 2)
        ));

        List<com.expensewise.admin.dto.FeatureUsageResponse> usage = service.getDashboard(6, 5).featureUsage();

        assertThat(usage).hasSize(Feature.values().length);
        // TRANSACTIONS has zero explicit disables, so every regular user
        // counts as enabled — including one with no row for it at all.
        assertThat(usage).filteredOn(f -> f.feature() == Feature.TRANSACTIONS)
                .singleElement().satisfies(f -> {
                    assertThat(f.enabledCount()).isEqualTo(4);
                    assertThat(f.percentage()).isEqualTo(100);
                });
        assertThat(usage).filteredOn(f -> f.feature() == Feature.BUDGETS)
                .singleElement().satisfies(f -> {
                    assertThat(f.enabledCount()).isEqualTo(2);
                    assertThat(f.percentage()).isEqualTo(50);
                });
        assertThat(usage).filteredOn(f -> f.feature() == Feature.CATEGORIES)
                .singleElement().satisfies(f -> {
                    assertThat(f.enabledCount()).isEqualTo(4);
                    assertThat(f.percentage()).isEqualTo(100);
                });
    }

    @Test
    void featureUsageTreatsAUserWithNoEntitlementRowAtAllAsStillEnabled() {
        // Models an account created before the entitlements table existed —
        // it has zero rows for every feature, not even a disabled one.
        when(userRepository.countByRole("USER")).thenReturn(1L);
        when(entitlementRepository.countDisabledByFeature()).thenReturn(List.of());

        List<com.expensewise.admin.dto.FeatureUsageResponse> usage = service.getDashboard(6, 5).featureUsage();

        assertThat(usage).isNotEmpty();
        assertThat(usage).allSatisfy(f -> {
            assertThat(f.enabledCount()).isEqualTo(1);
            assertThat(f.percentage()).isEqualTo(100);
        });
    }

    @Test
    void featureUsagePercentageIsZeroWhenThereAreNoRegularUsersRatherThanDivideByZero() {
        when(userRepository.countByRole("USER")).thenReturn(0L);

        List<com.expensewise.admin.dto.FeatureUsageResponse> usage = service.getDashboard(6, 5).featureUsage();

        assertThat(usage).isNotEmpty();
        assertThat(usage).allSatisfy(f -> assertThat(f.percentage()).isZero());
    }

    @Test
    void recentSignupsUsesTheConfiguredLimitAndMapsEachUser() {
        User user = new User();
        user.setId(42L);
        user.setFullName("Priya");
        user.setEmail("priya@example.com");
        when(userRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(user));
        when(dashboardMapper.toRecentSignupResponse(user)).thenReturn(
                new com.expensewise.admin.dto.RecentSignupResponse(42L, "Priya", "priya@example.com",
                        Instant.parse("2026-07-15T09:00:00Z"), true));

        List<com.expensewise.admin.dto.RecentSignupResponse> recent = service.getDashboard(6, 3).recentSignups();

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).fullName()).isEqualTo("Priya");
    }
}
