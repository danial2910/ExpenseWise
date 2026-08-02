package com.expensewise.user.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.user.dto.ChangePasswordRequest;
import com.expensewise.user.dto.LoginHistoryResponse;
import com.expensewise.user.dto.UpdateProfileRequest;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getCurrentUser(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getCurrentUser(principal.userId());
    }

    @PatchMapping
    public UserResponse updateProfile(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.userId(), request);
    }

    @PatchMapping("/password")
    public void changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.userId(), request);
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam("file") MultipartFile file) {
        return userService.uploadAvatar(principal.userId(), file);
    }

    @DeleteMapping("/avatar")
    public UserResponse removeAvatar(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.removeAvatar(principal.userId());
    }

    @GetMapping("/login-history")
    public Page<LoginHistoryResponse> loginHistory(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.getLoginHistory(principal.userId(), clamp(pageable));
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
