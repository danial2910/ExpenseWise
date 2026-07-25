package com.expensewise.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private final RateLimiterService rateLimiterService = new RateLimiterService();

    @Test
    void allowsUpToFiveAttemptsThenBlocksTheSixth() {
        String email = "sarah@example.com";

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiterService.isRateLimited(email)).isFalse();
            rateLimiterService.recordFailedAttempt(email);
        }

        assertThat(rateLimiterService.isRateLimited(email)).isTrue();
    }

    @Test
    void aSuccessfulAttemptClearsThePriorFailureCount() {
        String email = "sarah@example.com";

        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordFailedAttempt(email);
        }
        assertThat(rateLimiterService.isRateLimited(email)).isTrue();

        rateLimiterService.recordSuccessfulAttempt(email);

        assertThat(rateLimiterService.isRateLimited(email)).isFalse();
    }

    @Test
    void tracksEachEmailIndependently() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordFailedAttempt("locked-out@example.com");
        }

        assertThat(rateLimiterService.isRateLimited("locked-out@example.com")).isTrue();
        assertThat(rateLimiterService.isRateLimited("fresh@example.com")).isFalse();
    }

    @Test
    void emailMatchingIsCaseAndWhitespaceInsensitive() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordFailedAttempt("  Sarah@Example.com  ");
        }

        assertThat(rateLimiterService.isRateLimited("sarah@example.com")).isTrue();
    }
}
