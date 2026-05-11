package com.channel.integration.logging;

import com.channel.integration.circuitbreaker.CircuitBreakerState;

/**
 * Unified logging contract for the Integration Framework.
 *
 * <p>Interface Segregation: exposes only framework-relevant logging events.
 * Application-level logging remains the responsibility of the calling service.</p>
 *
 * <p>All log entries produced by implementations must include structured fields
 * (JSON format) to enable machine-readable ingestion by the Observability Platform.</p>
 */
public interface UnifiedLogger {

    /** Logs the start of an integration call. */
    void logRequest(String traceId, String serviceId, String idempotencyKey);

    /** Logs a successful integration call with duration. */
    void logSuccess(String traceId, String serviceId, long durationMs);

    /** Logs a failed integration call with error details. */
    void logFailure(String traceId, String serviceId, long durationMs, Throwable error);

    /** Logs a retry attempt with the computed delay. */
    void logRetryAttempt(String traceId, String serviceId, int attempt, int maxAttempts, long delayMs);

    /** Logs a circuit breaker state transition. */
    void logCircuitBreakerStateChange(String serviceId, CircuitBreakerState from, CircuitBreakerState to);

    /** Logs a cache hit for an idempotency key. */
    void logIdempotencyHit(String traceId, String serviceId, String idempotencyKey);
}
