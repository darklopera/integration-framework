package com.channel.integration.idempotency;

import com.channel.integration.config.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Manages idempotency key check-and-store for safe retries.
 *
 * <p>When a client provides an {@code X-Idempotency-Key} header, this manager
 * ensures that if the same operation is retried (due to network issues, timeouts,
 * or client retries), the stored result is returned without re-executing the
 * operation. This is critical for non-idempotent operations like payments.</p>
 *
 * <p>Single Responsibility: this class only handles deduplication logic.
 * It does not know about retries, circuit breakers, or timeouts.</p>
 */
@Component
public class IdempotencyManager {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyManager.class);

    private final IdempotencyStore store;
    private final IntegrationProperties.IdempotencyConfig config;

    public IdempotencyManager(IdempotencyStore store, IntegrationProperties properties) {
        this.store = store;
        this.config = properties.getIdempotency();
    }

    /**
     * Executes the callable only if the idempotency key has not been seen before.
     * If the key exists, returns the cached result without calling the callable.
     *
     * @param idempotencyKey the client-provided key (null = no deduplication, always executes)
     * @param callable       the operation to execute
     * @param <T>            return type
     * @return the result (fresh or cached)
     * @throws Exception if the callable fails
     */
    @SuppressWarnings("unchecked")
    public <T> T executeIfNew(String idempotencyKey, Callable<T> callable) throws Exception {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.debug("[Idempotency] No key provided — executing without deduplication");
            return callable.call();
        }

        Optional<Object> cached = store.get(idempotencyKey);
        if (cached.isPresent()) {
            log.info("[Idempotency] HIT for key={} — returning cached result, skipping execution", idempotencyKey);
            return (T) cached.get();
        }

        log.debug("[Idempotency] MISS for key={} — executing and caching result", idempotencyKey);
        T result = callable.call();
        store.put(idempotencyKey, result, config.getTtlMs());
        log.debug("[Idempotency] Result cached for key={} TTL={}ms", idempotencyKey, config.getTtlMs());
        return result;
    }

    /** Checks whether an idempotency key is already registered. */
    public boolean isAlreadyProcessed(String idempotencyKey) {
        return idempotencyKey != null && store.contains(idempotencyKey);
    }
}
