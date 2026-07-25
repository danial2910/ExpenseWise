package com.expensewise.auth.security;

/**
 * The authenticated principal derived from a validated access token.
 * Controllers/services must read the user id from here — never from a
 * request body or query string.
 */
public record AuthPrincipal(Long userId, String role) {
}
