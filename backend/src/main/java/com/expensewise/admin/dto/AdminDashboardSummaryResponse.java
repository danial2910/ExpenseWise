package com.expensewise.admin.dto;

public record AdminDashboardSummaryResponse(
        long totalUsers,
        long activeUsers,
        long disabledUsers,
        long newUsersThisMonth,
        long adminUsers,
        long regularUsers,
        long totalTransactions,
        long totalBudgets
) {
}
