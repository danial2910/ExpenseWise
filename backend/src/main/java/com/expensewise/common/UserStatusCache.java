package com.expensewise.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory cache of each user's {@code is_active} flag, so the JWT filter
 * can check it on every request without a database hit.
 *
 * <p>Populated lazily on first use and invalidated explicitly the moment an
 * admin enables/disables a user, so a disabled user is rejected on their
 * very next request with no polling or TTL lag.
 *
 * <p><b>Trade-off:</b> this is local JVM memory. It gives immediate
 * consistency at zero extra DB cost for a single backend instance, which
 * matches this project's deployment shape (one Spring Boot instance,
 * Supabase is DB-only). It would NOT stay consistent across multiple app
 * instances — each instance would have its own cache — which would require
 * a shared cache (Redis, explicitly out of scope) to fix.
 */
@Component
public class UserStatusCache {

    private final Map<Long, Boolean> activeByUserId = new ConcurrentHashMap<>();

    public boolean isActive(Long userId, Supplier<Boolean> loader) {
        return activeByUserId.computeIfAbsent(userId, id -> loader.get());
    }

    public void invalidate(Long userId) {
        activeByUserId.remove(userId);
    }
}
