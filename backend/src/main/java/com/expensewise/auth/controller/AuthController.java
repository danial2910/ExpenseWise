package com.expensewise.auth.controller;

import com.expensewise.auth.dto.ForgotPasswordRequest;
import com.expensewise.auth.dto.LoginRequest;
import com.expensewise.auth.dto.LoginResponse;
import com.expensewise.auth.dto.RefreshResponse;
import com.expensewise.auth.dto.RegisterRequest;
import com.expensewise.auth.dto.ResetPasswordRequest;
import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.auth.security.RefreshCookieFactory;
import com.expensewise.auth.service.AuthService;
import com.expensewise.auth.service.PasswordResetService;
import com.expensewise.auth.service.RefreshTokenService;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.exception.InvalidTokenException;
import com.expensewise.user.mapper.UserMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final ActivityLogger activityLogger;
    private final UserMapper userMapper;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(AuthService authService,
                           PasswordResetService passwordResetService,
                           RefreshTokenService refreshTokenService,
                           ActivityLogger activityLogger,
                           UserMapper userMapper,
                           RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
        this.activityLogger = activityLogger;
        this.userMapper = userMapper;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletResponse response) {
        AuthService.AuthResult result = authService.register(request);
        response.addCookie(refreshCookieFactory.create(result.refreshToken().rawValue()));
        LoginResponse body = new LoginResponse(result.accessToken(), userMapper.toResponse(result.user()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                 HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(request);
        response.addCookie(refreshCookieFactory.create(result.refreshToken().rawValue()));
        LoginResponse body = new LoginResponse(result.accessToken(), userMapper.toResponse(result.user()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new InvalidTokenException("Refresh token is missing");
        }
        AuthService.RefreshResult result = authService.refreshAccessToken(refreshToken);
        response.addCookie(refreshCookieFactory.create(result.rotatedToken().rawValue()));
        return ResponseEntity.ok(new RefreshResponse(result.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        Cookie clearCookie = refreshCookieFactory.clear();
        response.addCookie(clearCookie);
        return ResponseEntity.noContent().build();
    }

    // Lives here (not /api/v1/users/me) because it needs the refreshToken
    // cookie to identify which session is "current" and must be spared —
    // that cookie is deliberately Path-scoped to /api/v1/auth only (see
    // RefreshCookieFactory), so it's never sent to a /users/me endpoint.
    // /api/v1/auth/** is otherwise a public path (SecurityConfig), hence
    // the explicit @PreAuthorize here. See DECISIONS.md.
    @PostMapping("/logout-others")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logoutOthers(@AuthenticationPrincipal AuthPrincipal principal,
                                              @CookieValue(required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidTokenException("Refresh token is missing");
        }
        refreshTokenService.revokeAllForUserExcept(principal.userId(), refreshToken);
        activityLogger.log(principal.userId(), ActivityAction.LOGOUT_OTHERS);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Always the same response whether or not the email exists — no user enumeration.
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.completeReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
