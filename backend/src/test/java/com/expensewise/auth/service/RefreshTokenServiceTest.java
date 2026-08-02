package com.expensewise.auth.service;

import com.expensewise.auth.repository.RefreshTokenRepository;
import com.expensewise.common.TokenHasher;
import com.expensewise.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtProperties jwtProperties;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtProperties);
    }

    @Test
    void revokeAllForUserExceptExcludesTheCurrentSessionsHash() {
        String currentRawValue = "current-raw-token-value";
        String expectedHash = TokenHasher.sha256Hex(currentRawValue);

        refreshTokenService.revokeAllForUserExcept(USER_ID, currentRawValue);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository).revokeAllActiveForUserExcept(eq(USER_ID), hashCaptor.capture(), any(Instant.class));
        assertThat(hashCaptor.getValue()).isEqualTo(expectedHash);
    }
}
