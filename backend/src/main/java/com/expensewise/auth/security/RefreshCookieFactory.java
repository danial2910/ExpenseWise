package com.expensewise.auth.security;

import com.expensewise.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the httpOnly refresh-token cookie. Path-scoped to the auth
 * endpoints only, so it's never sent on unrelated requests. {@code Secure}
 * is off for local (plain HTTP) and on for prod (HTTPS) via the
 * {@code cookie.secure} property.
 */
@Component
public class RefreshCookieFactory {

    private static final String COOKIE_NAME = "refreshToken";

    private final JwtProperties jwtProperties;
    private final boolean secure;
    private final String cookiePath;

    public RefreshCookieFactory(JwtProperties jwtProperties, @Value("${cookie.secure}") boolean secure,
                                 @Value("${cookie.auth-path:/api/v1/auth}") String cookiePath) {
        this.jwtProperties = jwtProperties;
        this.secure = secure;
        this.cookiePath = cookiePath;
    }

    public Cookie create(String rawValue) {
        Cookie cookie = new Cookie(COOKIE_NAME, rawValue);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(cookiePath);
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge((int) jwtProperties.refreshTokenExpirationSeconds());
        return cookie;
    }

    public Cookie clear() {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(cookiePath);
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge(0);
        return cookie;
    }

    public String cookieName() {
        return COOKIE_NAME;
    }
}
