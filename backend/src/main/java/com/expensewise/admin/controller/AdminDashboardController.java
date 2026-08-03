package com.expensewise.admin.controller;

import com.expensewise.admin.dto.AdminDashboardResponse;
import com.expensewise.admin.service.AdminDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private static final int DEFAULT_MONTHS = 6;
    private static final int MAX_MONTHS = 24;
    private static final int DEFAULT_RECENT_LIMIT = 5;
    private static final int MAX_RECENT_LIMIT = 50;

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public AdminDashboardResponse getDashboard(@RequestParam(required = false) Integer months,
                                                @RequestParam(required = false) Integer recentLimit) {
        return adminDashboardService.getDashboard(clamp(months, DEFAULT_MONTHS, MAX_MONTHS),
                clamp(recentLimit, DEFAULT_RECENT_LIMIT, MAX_RECENT_LIMIT));
    }

    private int clamp(Integer value, int defaultValue, int max) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, max);
    }
}
