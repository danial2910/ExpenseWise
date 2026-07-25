package com.expensewise.auth.service;

import com.expensewise.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
            "test-signing-secret-that-is-long-enough-for-hmac-sha-256-algorithm";

    @Test
    void generatesATokenThatParsesBackToTheSameClaims() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 1800, 604800));

        String token = jwtService.generateAccessToken(42L, "USER");

        Optional<Claims> claims = jwtService.parse(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("42");
        assertThat(claims.get().get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void rejectsAnExpiredToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, -10, 604800));

        String token = jwtService.generateAccessToken(1L, "USER");

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void rejectsAMalformedToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 1800, 604800));

        assertThat(jwtService.parse("not-a-real-token")).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        JwtService issuer = new JwtService(new JwtProperties(SECRET, 1800, 604800));
        JwtService verifier = new JwtService(
                new JwtProperties("a-completely-different-signing-secret-of-sufficient-length", 1800, 604800));

        String token = issuer.generateAccessToken(1L, "USER");

        assertThat(verifier.parse(token)).isEmpty();
    }
}
