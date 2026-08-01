package com.expensewise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bound with explicit @Value placeholders naming the exact env var, not
 * @ConfigurationProperties relaxed binding — see GroqProperties for why
 * that matters. A plain, unrelated JWT_SECRET set on this machine for
 * another project was silently winning over JWT_SECRET_EXPENSEWISE before
 * this fix. See DECISIONS.md.
 */
@Component
public record JwtProperties(
        @Value("${JWT_SECRET_EXPENSEWISE}") String secret,
        @Value("${JWT_EXPIRATION_EXPENSEWISE:1800}") long accessTokenExpirationSeconds,
        @Value("${REFRESH_TOKEN_EXPIRATION_EXPENSEWISE:604800}") long refreshTokenExpirationSeconds
) {
}
