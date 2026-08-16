package com.expensewise.auth.security;

import com.expensewise.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieFactoryTest {

    private final JwtProperties jwtProperties = new JwtProperties("test-secret", 1800, 604800);

    @Test
    void localProfileCreatesLaxNonSecureCookie() {
        RefreshCookieFactory factory = new RefreshCookieFactory(jwtProperties, false, "/api/v1/auth", "Lax");

        Cookie cookie = factory.create("raw-refresh-token");

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo("raw-refresh-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(604800);
    }

    @Test
    void prodProfileCreatesNoneSecureCookie() {
        RefreshCookieFactory factory = new RefreshCookieFactory(jwtProperties, true, "/api/v1/auth", "None");

        Cookie cookie = factory.create("raw-refresh-token");

        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("None");
    }

    @Test
    void clearExpiresTheCookieUsingTheSameAttributes() {
        RefreshCookieFactory factory = new RefreshCookieFactory(jwtProperties, true, "/api/v1/auth", "None");

        Cookie cookie = factory.clear();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("None");
    }

    @Test
    void cookieNameIsRefreshToken() {
        RefreshCookieFactory factory = new RefreshCookieFactory(jwtProperties, false, "/api/v1/auth", "Lax");

        assertThat(factory.cookieName()).isEqualTo("refreshToken");
    }
}
