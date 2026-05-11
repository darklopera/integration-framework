package com.channel.integration.circuitbreaker;

import com.channel.integration.config.IntegrationProperties;
import com.channel.integration.exception.CircuitBreakerOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe Circuit Breaker implementation using a sliding window of call results.
 *
 * <p>Each serviceId maintains its own independent breaker state, allowing fine-grained
 * control per downstream dependency. Uses a {@link ConcurrentHashMap} to support
 * concurrent access from multiple backend instances.</p>
 *
 * <p>Open/Closed Principle: the breaker logic is sealed here. To change the state
 * machine algorithm, provide an alternative {@link CircuitBreaker} implementation
 * and inject it via Spring — no modifications to framework classes required.</p>
 */
@Component
public class DefaultCircuitBreaker implements CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(DefaultCircuitBreaker.class);

    private final IntegrationProperties.CircuitBreakerConfig config;
    private final ConcurrentHashMap<String, BreakerEntry> breakers = new ConcurrentHashMap<>();

    public DefaultCircuitBreaker(IntegrationProperties properties) {
        this.config = properties.getCircuitBreaker();
    }

    @Override
    public <T> T execute(String serviceId, Callable<T> callable) throws Exception {
        BreakerEntry entry = breakers.computeIfAbsent(serviceId, id -> new BreakerEntry());

        synchronized (entry) {
            CircuitBreakerState currentState = resolveState(entry);

            switch (currentState) {
                case OPEN -> {
                    log.warn("[CircuitBreaker] OPEN - Rejecting call to serviceId={}", serviceId);
                    throw new CircuitBreakerOpenException(serviceId);
                }
                case HALF_OPEN -> {
                    if (entry.halfOpenCallCount.get() >= config.getHalfOpenMaxCalls()) {
                        log.warn("[CircuitBreaker] HALF_OPEN probe limit reached - Rejecting call to serviceId={}", serviceId);
                        throw new CircuitBreakerOpenException(serviceId);
                    }
                    entry.halfOpenCallCount.incrementAndGet();
                }
                case CLOSED -> { /* proceed normally */ }
            }
        }

        // Execute the callable outside the synchronized block to avoid holding the lock
        try {
            T result = callable.call();
            recordSuccess(serviceId, entry);
            return result;
        } catch (Exception ex) {
            recordFailure(serviceId, entry);
            throw ex;
        }
    }

    @Override
    public CircuitBreakerState getState(String serviceId) {
        BreakerEntry entry = breakers.get(serviceId);
        if (entry == null) return CircuitBreakerState.CLOSED;
        synchronized (entry) {
            return resolveState(entry);
        }
    }

    @Override
    public void reset(String serviceId) {
        BreakerEntry entry = breakers.get(serviceId);
        if (entry != null) {
            synchronized (entry) {
                entry.state = CircuitBreakerState.CLOSED;
                entry.window.clear();
                entry.halfOpenCallCount.set(0);
                entry.openedAt = null;
                log.info("[CircuitBreaker] Manually RESET for serviceId={}", serviceId);
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private CircuitBreakerState resolveState(BreakerEntry entry) {
        if (entry.state == CircuitBreakerState.OPEN) {
            long elapsed = Instant.now().toEpochMilli() - entry.openedAt.toEpochMilli();
            if (elapsed >= config.getResetTimeoutMs()) {
                entry.state = CircuitBreakerState.HALF_OPEN;
                entry.halfOpenCallCount.set(0);
                log.info("[CircuitBreaker] Transitioning OPEN → HALF_OPEN after reset timeout");
            }
        }
        return entry.state;
    }

    private void recordSuccess(String serviceId, BreakerEntry entry) {
        synchronized (entry) {
            pruneWindow(entry);
            entry.window.addLast(true);

            if (entry.state == CircuitBreakerState.HALF_OPEN) {
                // All probes succeeded → recover
                entry.state = CircuitBreakerState.CLOSED;
                entry.window.clear();
                entry.halfOpenCallCount.set(0);
                log.info("[CircuitBreaker] HALF_OPEN → CLOSED (recovery confirmed) for serviceId={}", serviceId);
            }
        }
    }

    private void recordFailure(String serviceId, BreakerEntry entry) {
        synchronized (entry) {
            pruneWindow(entry);
            entry.window.addLast(false);

            if (entry.state == CircuitBreakerState.HALF_OPEN) {
                tripBreaker(serviceId, entry);
                return;
            }

            if (shouldTrip(entry)) {
                tripBreaker(serviceId, entry);
            }
        }
    }

    private boolean shouldTrip(BreakerEntry entry) {
        if (entry.window.size() < config.getMinimumCallsInWindow()) return false;
        long failures = entry.window.stream().filter(r -> !r).count();
        double errorRate = (double) failures / entry.window.size() * 100.0;
        return errorRate >= config.getErrorThresholdPercent();
    }

    private void tripBreaker(String serviceId, BreakerEntry entry) {
        entry.state = CircuitBreakerState.OPEN;
        entry.openedAt = Instant.now();
        entry.window.clear();
        log.error("[CircuitBreaker] TRIPPED → OPEN for serviceId={} - System protected from cascade failure", serviceId);
    }

    private void pruneWindow(BreakerEntry entry) {
        long cutoff = Instant.now().toEpochMilli() - config.getWindowSizeMs();
        while (!entry.timestamps.isEmpty() && entry.timestamps.peekFirst() < cutoff) {
            entry.timestamps.pollFirst();
            if (!entry.window.isEmpty()) entry.window.pollFirst();
        }
        entry.timestamps.addLast(Instant.now().toEpochMilli());
    }

    // ── Internal state holder per service ────────────────────────────────

    private static class BreakerEntry {
        volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
        final Deque<Boolean> window = new ArrayDeque<>();
        final Deque<Long> timestamps = new ArrayDeque<>();
        final AtomicInteger halfOpenCallCount = new AtomicInteger(0);
        volatile Instant openedAt = null;
    }
}
