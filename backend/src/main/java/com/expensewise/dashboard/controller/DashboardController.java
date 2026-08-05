package com.expensewise.dashboard.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.dashboard.dto.DashboardResponse;
import com.expensewise.dashboard.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * USER-only personal home screen (SecurityConfig.USER_ONLY_PATHS) — ADMIN
 * accounts have their own Admin Dashboard instead. Not gated behind
 * RequiresFeature/Feature, same as Profile: it has no admin-toggleable
 * entitlement, it's always available to a USER account.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final int DEFAULT_MONTHS = 6;
    private static final int MAX_MONTHS = 24;
    private static final int DEFAULT_RECENT_LIMIT = 5;
    private static final int MAX_RECENT_LIMIT = 50;

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboard(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(required = false) Integer months,
                                           @RequestParam(required = false) Integer recentLimit) {
        return dashboardService.getDashboard(principal.userId(),
                clamp(months, DEFAULT_MONTHS, MAX_MONTHS),
                clamp(recentLimit, DEFAULT_RECENT_LIMIT, MAX_RECENT_LIMIT));
    }

    private int clamp(Integer value, int defaultValue, int max) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, max);
    }
}
