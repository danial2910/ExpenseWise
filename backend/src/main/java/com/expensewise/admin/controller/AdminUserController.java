package com.expensewise.admin.controller;

import com.expensewise.admin.dto.AdminCreateUserRequest;
import com.expensewise.admin.dto.AdminUpdateUserAccessRequest;
import com.expensewise.admin.dto.AdminUpdateUserRequest;
import com.expensewise.admin.dto.AdminUserDetailResponse;
import com.expensewise.admin.dto.UserStatusRequest;
import com.expensewise.admin.service.AdminUserService;
import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) Boolean active,
                                         @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return adminUserService.listUsers(search, role, active, clamp(pageable));
    }

    @GetMapping("/{id}")
    public AdminUserDetailResponse getUser(@PathVariable Long id) {
        return adminUserService.getUserDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDetailResponse createUser(@Valid @RequestBody AdminCreateUserRequest request,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return adminUserService.createUser(request, principal.userId());
    }

    @PatchMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest request) {
        return adminUserService.updateUser(id, request);
    }

    @PutMapping("/{id}/access")
    public AdminUserDetailResponse updateAccess(@PathVariable Long id,
                                                 @Valid @RequestBody AdminUpdateUserAccessRequest request,
                                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return adminUserService.updateAccess(id, request, principal.userId());
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UserStatusRequest request,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        return adminUserService.setStatus(id, request.active(), principal.userId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        adminUserService.deleteUser(id, principal.userId());
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
