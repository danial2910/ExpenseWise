package com.expensewise.auth.service;

import com.expensewise.auth.dto.LoginRequest;
import com.expensewise.auth.dto.RegisterRequest;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.exception.AccountDisabledException;
import com.expensewise.exception.EmailAlreadyExistsException;
import com.expensewise.exception.InvalidCredentialsException;
import com.expensewise.exception.InvalidTokenException;
import com.expensewise.exception.RateLimitedException;
import com.expensewise.exception.WeakPasswordException;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiterService rateLimiterService;
    private final ActivityLogger activityLogger;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        PasswordPolicyValidator passwordPolicyValidator,
                        JwtService jwtService,
                        RefreshTokenService refreshTokenService,
                        RateLimiterService rateLimiterService,
                        ActivityLogger activityLogger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiterService = rateLimiterService;
        this.activityLogger = activityLogger;
    }

    public record AuthResult(User user, String accessToken, RefreshTokenService.IssuedToken refreshToken) {
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalize(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }
        if (!passwordPolicyValidator.isValid(request.password())) {
            throw new WeakPasswordException(
                    "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, and a number");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user.getId());
        return new AuthResult(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = normalize(request.email());

        if (rateLimiterService.isRateLimited(email)) {
            throw new RateLimitedException("Too many login attempts. Try again later.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiterService.recordFailedAttempt(email);
            activityLogger.log(user == null ? null : user.getId(), ActivityAction.LOGIN_FAILED);
            throw new InvalidCredentialsException("Incorrect email or password");
        }

        if (!user.isActive()) {
            rateLimiterService.recordFailedAttempt(email);
            activityLogger.log(user.getId(), ActivityAction.LOGIN_FAILED);
            throw new AccountDisabledException("This account has been disabled");
        }

        rateLimiterService.recordSuccessfulAttempt(email);
        activityLogger.log(user.getId(), ActivityAction.LOGIN_SUCCESS);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user.getId());
        return new AuthResult(user, accessToken, refreshToken);
    }

    public record RefreshResult(String accessToken, RefreshTokenService.RotatedToken rotatedToken) {
    }

    // Deliberately not @Transactional: rotate() below is its own transaction with
    // noRollbackFor(InvalidTokenException) so the reuse-detection revocation commits
    // even though it then throws. Wrapping this method too would make refreshAccessToken()
    // the outer (owning) transaction, whose default rollback rules would override that.
    public RefreshResult refreshAccessToken(String rawRefreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        return new RefreshResult(accessToken, rotated);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken)
                .ifPresent(userId -> activityLogger.log(userId, ActivityAction.LOGOUT));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
