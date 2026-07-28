package com.expensewise.budget.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.budget.dto.BudgetMonthResponse;
import com.expensewise.budget.dto.BudgetRequest;
import com.expensewise.budget.dto.BudgetResponse;
import com.expensewise.budget.dto.PatchBudgetRequest;
import com.expensewise.budget.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public BudgetMonthResponse getMonthBudgets(@AuthenticationPrincipal AuthPrincipal principal,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return budgetService.getMonthBudgets(principal.userId(), month);
    }

    @GetMapping("/{id}")
    public BudgetResponse getBudget(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return budgetService.getBudget(principal.userId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse createBudget(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody BudgetRequest request) {
        return budgetService.createBudget(principal.userId(), request);
    }

    @PutMapping("/{id}")
    public BudgetResponse updateBudget(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable Long id,
                                        @Valid @RequestBody BudgetRequest request) {
        return budgetService.updateBudget(principal.userId(), id, request);
    }

    @PatchMapping("/{id}")
    public BudgetResponse patchBudget(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long id,
                                       @Valid @RequestBody PatchBudgetRequest request) {
        return budgetService.patchBudget(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBudget(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        budgetService.deleteBudget(principal.userId(), id);
    }
}
