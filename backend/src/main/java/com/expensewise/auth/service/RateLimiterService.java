package com.expensewise.auth.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window login attempt limiter: 5 attempts per 15 minutes
 * per email. Same single-instance trade-off as {@link com.expensewise.common.UserStatusCache} —
 * fine for this project's one-instance deployment, would need a shared store
 * (Redis, out of scope) to work correctly behind multiple app instances.
 */
@Service
public class RateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> attemptsByEmail = new ConcurrentHashMap<>();

    public boolean isRateLimited(String email) {
        Deque<Instant> attempts = attemptsByEmail.computeIfAbsent(normalize(email), k -> new ArrayDeque<>());
        synchronized (attempts) {
            evictExpired(attempts);
            return attempts.size() >= MAX_ATTEMPTS;
        }
    }

    public void recordFailedAttempt(String email) {
        Deque<Instant> attempts = attemptsByEmail.computeIfAbsent(normalize(email), k -> new ArrayDeque<>());
        synchronized (attempts) {
            evictExpired(attempts);
            attempts.addLast(Instant.now());
        }
    }

    public void recordSuccessfulAttempt(String email) {
        attemptsByEmail.remove(normalize(email));
    }

    private void evictExpired(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.pollFirst();
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
