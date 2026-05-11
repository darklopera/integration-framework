package com.channel.integration.idempotency;

import java.util.Optional;

/**
 * Storage contract for idempotency keys and their associated results.
 *
 * <p>Dependency Inversion: the framework depends on this abstraction.
 * Production deployments can replace the in-memory store with a Redis-backed
 * implementation for distributed idempotency across multiple instances,
 * with zero changes to the framework code.</p>
 */
public interface IdempotencyStore {

    /**
     * Retrieves a previously stored result for the given key.
     *
     * @param key the idempotency key
     * @return an Optional containing the cached result, or empty if not found / expired
     */
    Optional<Object> get(String key);

    /**
     * Stores a result associated with the given key.
     *
     * @param key      the idempotency key
     * @param result   the result to cache
     * @param ttlMs    time-to-live in milliseconds
     */
    void put(String key, Object result, long ttlMs);

    /**
     * Returns true if the key exists and has not expired.
     */
    boolean contains(String key);
}
