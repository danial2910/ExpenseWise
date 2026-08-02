package com.expensewise.auth.service;

import com.expensewise.auth.entity.RefreshToken;
import com.expensewise.auth.repository.RefreshTokenRepository;
import com.expensewise.common.TokenHasher;
import com.expensewise.config.JwtProperties;
import com.expensewise.exception.InvalidTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Issues, rotates, and revokes refresh tokens. Tokens are opaque random
 * values (not JWTs) stored hashed. Rotated on every use; reuse of an
 * already-revoked token is treated as a compromise signal and revokes
 * every active token for that user (see DECISIONS.md — the given schema
 * has no per-device family column, so "family" == "all of that user's
 * sessions").
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    public record IssuedToken(String rawValue, Instant expiresAt) {
    }

    @Transactional
    public IssuedToken issue(Long userId) {
        String rawValue = TokenHasher.generateRawToken();
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTokenExpirationSeconds());

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(TokenHasher.sha256Hex(rawValue));
        entity.setExpiresAt(expiresAt);
        refreshTokenRepository.save(entity);

        return new IssuedToken(rawValue, expiresAt);
    }

    public record RotatedToken(Long userId, String rawValue, Instant expiresAt) {
    }

    /**
     * Validates the presented refresh token and rotates it: the old row is
     * revoked and a new token is issued for the same user. Throws
     * {@link InvalidTokenException} if the token is unknown, expired, or
     * already revoked (in the revoked case, every other active token for
     * that user is revoked too, per the reuse-detection requirement).
     */
    // noRollbackFor: the revoked-reuse branch below must persist its revocation of the
    // rest of the family even though the method then throws to reject the reuse attempt.
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public RotatedToken rotate(String rawValue) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawValue))
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));

        if (existing.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllActiveForUser(existing.getUserId(), Instant.now());
            throw new InvalidTokenException("Refresh token has already been used");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        existing.setRevokedAt(Instant.now());

        IssuedToken next = issue(existing.getUserId());
        return new RotatedToken(existing.getUserId(), next.rawValue(), next.expiresAt());
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    /**
     * Revokes every other active token for the user, leaving the session
     * identified by {@code currentRawValue} untouched — "log out of all
     * other sessions." The current session is identified by re-hashing the
     * raw refresh cookie value already presented on this request.
     */
    @Transactional
    public void revokeAllForUserExcept(Long userId, String currentRawValue) {
        String currentTokenHash = TokenHasher.sha256Hex(currentRawValue);
        refreshTokenRepository.revokeAllActiveForUserExcept(userId, currentTokenHash, Instant.now());
    }

    /**
     * Revokes a single token (used on logout). Returns the owning user id,
     * if the token was found, so the caller can write an activity log entry.
     */
    @Transactional
    public Optional<Long> revoke(String rawValue) {
        return refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawValue))
                .map(token -> {
                    token.setRevokedAt(Instant.now());
                    return token.getUserId();
                });
    }
}
