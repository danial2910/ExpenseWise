package com.expensewise.auth.service;

import com.expensewise.auth.entity.PasswordResetToken;
import com.expensewise.auth.repository.PasswordResetTokenRepository;
import com.expensewise.common.ActivityAction;
import com.expensewise.common.ActivityLogger;
import com.expensewise.exception.InvalidTokenException;
import com.expensewise.user.entity.User;
import com.expensewise.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "reset@example.com";
    private static final String FRONTEND_URL = "http://localhost:5173";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private MailService mailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private ActivityLogger activityLogger;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(userRepository, passwordResetTokenRepository,
                mailService, passwordEncoder, refreshTokenService, activityLogger, FRONTEND_URL);
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setPasswordHash("old-hash");
        return user;
    }

    private PasswordResetToken tokenFor(String hash, Instant expiresAt, Instant usedAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(99L);
        token.setUserId(USER_ID);
        token.setTokenHash(hash);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return token;
    }

    @Test
    void requestResetDoesNothingWhenNoUserMatchesTheEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        passwordResetService.requestReset(EMAIL);

        verify(passwordResetTokenRepository, never()).invalidateActiveForUser(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
        verify(activityLogger, never()).log(any(), anyString());
    }

    @Test
    void requestResetNormalizesEmailCaseAndWhitespaceBeforeLookup() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        passwordResetService.requestReset("  Reset@Example.com  ");

        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void requestResetInvalidatesPriorTokensThenSavesAndEmailsANewOne() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        passwordResetService.requestReset(EMAIL);

        verify(passwordResetTokenRepository).invalidateActiveForUser(eq(USER_ID), any(Instant.class));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(29, ChronoUnit.MINUTES));
        assertThat(saved.getExpiresAt()).isBefore(Instant.now().plus(31, ChronoUnit.MINUTES));

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetEmail(eq(EMAIL), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith(FRONTEND_URL + "/reset-password?token=");

        verify(activityLogger).log(USER_ID, ActivityAction.PASSWORD_RESET_REQUESTED);
    }

    @Test
    void completeResetRejectsATokenThatDoesNotExist() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.completeReset("does-not-exist", "NewPassw0rd1"))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void completeResetRejectsAnAlreadyUsedToken() {
        PasswordResetToken token = tokenFor("hash", Instant.now().plusSeconds(60), Instant.now().minusSeconds(60));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.completeReset("raw-value", "NewPassw0rd1"))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).findById(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void completeResetRejectsAnExpiredToken() {
        PasswordResetToken token = tokenFor("hash", Instant.now().minusSeconds(1), null);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.completeReset("raw-value", "NewPassw0rd1"))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).findById(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void completeResetUpdatesThePasswordMarksTheTokenUsedAndRevokesRefreshTokens() {
        PasswordResetToken token = tokenFor("hash", Instant.now().plusSeconds(60), null);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassw0rd1")).thenReturn("new-hash");

        passwordResetService.completeReset("raw-value", "NewPassw0rd1");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isNotNull();
        verify(refreshTokenService).revokeAllForUser(USER_ID);
        verify(activityLogger).log(USER_ID, ActivityAction.PASSWORD_CHANGED);
    }

    @Test
    void completeResetRejectsATokenWhoseUserNoLongerExists() {
        PasswordResetToken token = tokenFor("hash", Instant.now().plusSeconds(60), null);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.completeReset("raw-value", "NewPassw0rd1"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never()).revokeAllForUser(any());
    }
}
