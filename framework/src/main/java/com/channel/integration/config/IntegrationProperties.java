package com.channel.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized configuration for the Integration Framework.
 *
 * <p>All tunable parameters live here — no hardcoded values in any other class.
 * Follows the Single Responsibility Principle: this class is solely responsible
 * for holding configuration, not applying it.</p>
 *
 * <p>Configurable via application.yml under the "integration" prefix.</p>
 */
@Component
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    private TimeoutConfig timeout = new TimeoutConfig();
    private RetryConfig retry = new RetryConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private IdempotencyConfig idempotency = new IdempotencyConfig();
    private TracingConfig tracing = new TracingConfig();

    // ── Getters & Setters ──────────────────────────────────────────────────

    public TimeoutConfig getTimeout() { return timeout; }
    public void setTimeout(TimeoutConfig timeout) { this.timeout = timeout; }

    public RetryConfig getRetry() { return retry; }
    public void setRetry(RetryConfig retry) { this.retry = retry; }

    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public IdempotencyConfig getIdempotency() { return idempotency; }
    public void setIdempotency(IdempotencyConfig idempotency) { this.idempotency = idempotency; }

    public TracingConfig getTracing() { return tracing; }
    public void setTracing(TracingConfig tracing) { this.tracing = tracing; }

    // ── Inner configuration classes ────────────────────────────────────────

    /**
     * Timeout configuration.
     * Supports a global default and per-service overrides.
     */
    public static class TimeoutConfig {
        /** Default timeout for all service calls in milliseconds. */
        private long defaultMs = 5000;

        /**
         * Per-service timeout overrides. Key = serviceId, Value = timeout in ms.
         * Example: payments-core: 8000
         */
        private Map<String, Long> perService = new HashMap<>();

        public long getDefaultMs() { return defaultMs; }
        public void setDefaultMs(long defaultMs) { this.defaultMs = defaultMs; }

        public Map<String, Long> getPerService() { return perService; }
        public void setPerService(Map<String, Long> perService) { this.perService = perService; }

        /** Returns the effective timeout for a given serviceId. */
        public long getEffectiveTimeoutMs(String serviceId) {
            return perService.getOrDefault(serviceId, defaultMs);
        }
    }

    /**
     * Retry configuration with exponential backoff and jitter.
     *
     * <p>Delay formula: min(baseDelayMs * (multiplier ^ attempt), maxDelayMs)
     * + random jitter of ±jitterFactor percent.</p>
     */
    public static class RetryConfig {
        /** Maximum number of retry attempts (not counting the first call). */
        private int maxAttempts = 3;

        /** Base delay in ms before the first retry. */
        private long baseDelayMs = 500;

        /** Multiplier applied to the delay on each successive retry. */
        private double multiplier = 2.0;

        /** Maximum delay cap in ms to prevent unbounded waits. */
        private long maxDelayMs = 10000;

        /**
         * Jitter factor as a fraction (0.0–1.0).
         * 0.2 means ±20% random variation is added to the computed delay.
         * Prevents thundering-herd when many instances retry simultaneously.
         */
        private double jitterFactor = 0.2;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public long getBaseDelayMs() { return baseDelayMs; }
        public void setBaseDelayMs(long baseDelayMs) { this.baseDelayMs = baseDelayMs; }

        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }

        public double getJitterFactor() { return jitterFactor; }
        public void setJitterFactor(double jitterFactor) { this.jitterFactor = jitterFactor; }
    }

    /**
     * Circuit Breaker configuration.
     *
     * <p>State machine: CLOSED → (threshold exceeded) → OPEN → (reset timeout) → HALF_OPEN
     * → (probes succeed) → CLOSED | (probes fail) → OPEN</p>
     */
    public static class CircuitBreakerConfig {
        /** Percentage of failures in the window to trip the breaker. */
        private double errorThresholdPercent = 50.0;

        /** Sliding window size in milliseconds. */
        private long windowSizeMs = 10000;

        /** Minimum calls in the window before the threshold is evaluated. */
        private int minimumCallsInWindow = 5;

        /** Number of probe calls allowed when in HALF_OPEN state. */
        private int halfOpenMaxCalls = 3;

        /** Time in ms to wait in OPEN before transitioning to HALF_OPEN. */
        private long resetTimeoutMs = 30000;

        public double getErrorThresholdPercent() { return errorThresholdPercent; }
        public void setErrorThresholdPercent(double errorThresholdPercent) { this.errorThresholdPercent = errorThresholdPercent; }

        public long getWindowSizeMs() { return windowSizeMs; }
        public void setWindowSizeMs(long windowSizeMs) { this.windowSizeMs = windowSizeMs; }

        public int getMinimumCallsInWindow() { return minimumCallsInWindow; }
        public void setMinimumCallsInWindow(int minimumCallsInWindow) { this.minimumCallsInWindow = minimumCallsInWindow; }

        public int getHalfOpenMaxCalls() { return halfOpenMaxCalls; }
        public void setHalfOpenMaxCalls(int halfOpenMaxCalls) { this.halfOpenMaxCalls = halfOpenMaxCalls; }

        public long getResetTimeoutMs() { return resetTimeoutMs; }
        public void setResetTimeoutMs(long resetTimeoutMs) { this.resetTimeoutMs = resetTimeoutMs; }
    }

    /**
     * Idempotency key configuration.
     */
    public static class IdempotencyConfig {
        /** TTL for idempotency keys in ms. Default: 24 hours. */
        private long ttlMs = 86_400_000L;

        /** HTTP header name carrying the idempotency key. */
        private String headerName = "X-Idempotency-Key";

        public long getTtlMs() { return ttlMs; }
        public void setTtlMs(long ttlMs) { this.ttlMs = ttlMs; }

        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }
    }

    /**
     * OpenTelemetry tracing configuration.
     */
    public static class TracingConfig {
        /** Logical name of this service in traces. */
        private String serviceName = "integration-framework";

        /** OTLP exporter endpoint. */
        private String exporterEndpoint = "http://localhost:4318/v1/traces";

        /** Whether tracing is enabled. */
        private boolean enabled = true;

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getExporterEndpoint() { return exporterEndpoint; }
        public void setExporterEndpoint(String exporterEndpoint) { this.exporterEndpoint = exporterEndpoint; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
