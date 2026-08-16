package com.expensewise.auth.security;

import com.expensewise.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the httpOnly refresh-token cookie. Path-scoped to the auth
 * endpoints only, so it's never sent on unrelated requests. {@code Secure}
 * is off for local (plain HTTP) and on for prod (HTTPS) via the
 * {@code cookie.secure} property. {@code SameSite} is Lax for local (same
 * site, just different ports) and None for prod (Vercel frontend, Render
 * backend — genuinely cross-site) via the {@code cookie.same-site} property;
 * None requires Secure, which prod already sets true.
 */
@Component
public class RefreshCookieFactory {

    private static final String COOKIE_NAME = "refreshToken";

    private final JwtProperties jwtProperties;
    private final boolean secure;
    private final String cookiePath;
    private final String sameSite;

    public RefreshCookieFactory(JwtProperties jwtProperties, @Value("${cookie.secure}") boolean secure,
                                 @Value("${cookie.auth-path:/api/v1/auth}") String cookiePath,
                                 @Value("${cookie.same-site:Lax}") String sameSite) {
        this.jwtProperties = jwtProperties;
        this.secure = secure;
        this.cookiePath = cookiePath;
        this.sameSite = sameSite;
    }

    public Cookie create(String rawValue) {
        Cookie cookie = new Cookie(COOKIE_NAME, rawValue);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(cookiePath);
        cookie.setAttribute("SameSite", sameSite);
        cookie.setMaxAge((int) jwtProperties.refreshTokenExpirationSeconds());
        return cookie;
    }

    public Cookie clear() {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(cookiePath);
        cookie.setAttribute("SameSite", sameSite);
        cookie.setMaxAge(0);
        return cookie;
    }

    public String cookieName() {
        return COOKIE_NAME;
    }
}
