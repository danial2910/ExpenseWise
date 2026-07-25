package com.expensewise.admin.controller;

import com.expensewise.admin.dto.AdminUpdateUserRequest;
import com.expensewise.admin.dto.UserStatusRequest;
import com.expensewise.admin.service.AdminUserService;
import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Page<UserResponse> listUsers(@RequestParam(required = false) String search,
                                         @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return adminUserService.listUsers(search, clamp(pageable));
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return adminUserService.getUser(id);
    }

    @PatchMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest request) {
        return adminUserService.updateUser(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UserStatusRequest request,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        return adminUserService.setStatus(id, request.active(), principal.userId());
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
