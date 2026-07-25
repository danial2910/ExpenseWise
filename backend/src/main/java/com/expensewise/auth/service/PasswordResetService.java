package com.expensewise.auth.service;

import com.expensewise.auth.entity.PasswordResetToken;
import com.expensewise.auth.repository.PasswordResetTokenRepository;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.common.TokenHasher;
import com.expensewise.exception.InvalidTokenException;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Handles the forgot/reset password flow. Never reveals whether an email
 * exists (requirement #3): {@link #requestReset} always completes silently,
 * whether or not a matching user is found.
 */
@Service
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ActivityLogger activityLogger;
    private final String frontendUrl;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 MailService mailService,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService,
                                 ActivityLogger activityLogger,
                                 @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.activityLogger = activityLogger;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void requestReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();

        Instant now = Instant.now();
        passwordResetTokenRepository.invalidateActiveForUser(user.getId(), now);

        String rawValue = TokenHasher.generateRawToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(TokenHasher.sha256Hex(rawValue));
        token.setExpiresAt(now.plus(TOKEN_TTL));
        passwordResetTokenRepository.save(token);

        String resetLink = frontendUrl + "/reset-password?token=" + rawValue;
        mailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        activityLogger.log(user.getId(), ActivityAction.PASSWORD_RESET_REQUESTED);
    }

    @Transactional
    public void completeReset(String rawValue, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawValue))
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid"));

        if (token.getUsedAt() != null) {
            throw new InvalidTokenException("Reset token has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Reset token has expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(Instant.now());

        refreshTokenService.revokeAllForUser(user.getId());
        activityLogger.log(user.getId(), ActivityAction.PASSWORD_CHANGED);
    }
}
