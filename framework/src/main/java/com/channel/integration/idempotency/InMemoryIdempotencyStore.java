package com.channel.integration.idempotency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * In-memory implementation of {@link IdempotencyStore} backed by Caffeine.
 *
 * <p>Uses a per-entry TTL based on the framework configuration.
 * For distributed deployments, replace this with a Redis-backed implementation
 * that implements the same {@link IdempotencyStore} interface.</p>
 *
 * <p>Caffeine provides O(1) reads and automatic eviction — suitable for
 * high-throughput scenarios.</p>
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    // Fixed-duration cache; per-entry TTL is managed via expiring entries
    private final Cache<String, CachedEntry> cache;

    public InMemoryIdempotencyStore() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .build();
    }

    @Override
    public Optional<Object> get(String key) {
        CachedEntry entry = cache.getIfPresent(key);
        if (entry == null || entry.isExpired()) {
            cache.invalidate(key);
            return Optional.empty();
        }
        return Optional.of(entry.result);
    }

    @Override
    public void put(String key, Object result, long ttlMs) {
        cache.put(key, new CachedEntry(result, System.currentTimeMillis() + ttlMs));
    }

    @Override
    public boolean contains(String key) {
        CachedEntry entry = cache.getIfPresent(key);
        if (entry == null) return false;
        if (entry.isExpired()) {
            cache.invalidate(key);
            return false;
        }
        return true;
    }

    private record CachedEntry(Object result, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
