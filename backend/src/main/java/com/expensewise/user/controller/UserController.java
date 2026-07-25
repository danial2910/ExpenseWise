package com.expensewise.user.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.user.dto.ChangePasswordRequest;
import com.expensewise.user.dto.UpdateProfileRequest;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

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
}
