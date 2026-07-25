package com.expensewise.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusCacheTest {

    private final UserStatusCache cache = new UserStatusCache();

    @Test
    void loaderIsOnlyCalledOnceUntilInvalidated() {
        AtomicInteger loadCount = new AtomicInteger();

        cache.isActive(1L, () -> {
            loadCount.incrementAndGet();
            return true;
        });
        cache.isActive(1L, () -> {
            loadCount.incrementAndGet();
            return true;
        });

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void invalidateForcesTheNextCallToReload() {
        cache.isActive(1L, () -> true);
        cache.invalidate(1L);

        boolean reloaded = cache.isActive(1L, () -> false);

        assertThat(reloaded).isFalse();
    }
}
