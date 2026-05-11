package com.channel.integration.framework;

import com.channel.integration.circuitbreaker.CircuitBreaker;
import com.channel.integration.idempotency.IdempotencyManager;
import com.channel.integration.logging.UnifiedLogger;
import com.channel.integration.retry.RetryStrategy;
import com.channel.integration.timeout.TimeoutPolicy;
import com.channel.integration.tracing.TracePropagator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Central orchestrator of the Integration Framework pipeline.
 *
 * <p>Composes all patterns in the correct order for every integration call:</p>
 * <pre>
 *  execute(request)
 *    │
 *    ├─ 1. IdempotencyManager  → short-circuit if already processed
 *    ├─ 2. TracePropagator     → start span + inject W3C trace context
 *    ├─ 3. UnifiedLogger       → log request start
 *    ├─ 4. CircuitBreaker      → fail-fast if OPEN
 *    ├─ 5. RetryStrategy       → wrap with exponential backoff + jitter
 *    │      └─ 6. TimeoutPolicy  → enforce per-call timeout
 *    │              └─ operation  → actual downstream call
 *    ├─ 7. CircuitBreaker.record → record success/failure
 *    ├─ 8. UnifiedLogger        → log result + duration
 *    └─ 9. TracePropagator.end  → close span, export to OTel collector
 * </pre>
 *
 * <p>Single Responsibility: this class only orchestrates. Each concern
 * (retry, timeout, circuit breaker, etc.) lives in its own class.</p>
 *
 * <p>Dependency Inversion: all collaborators are injected as interfaces.
 * Swapping an implementation requires only changing the Spring bean — no
 * modifications to this class.</p>
 */
@Component
public class IntegrationFramework {

    private final CircuitBreaker circuitBreaker;
    private final RetryStrategy retryStrategy;
    private final TimeoutPolicy timeoutPolicy;
    private final IdempotencyManager idempotencyManager;
    private final UnifiedLogger logger;
    private final TracePropagator tracePropagator;

    public IntegrationFramework(
            CircuitBreaker circuitBreaker,
            RetryStrategy retryStrategy,
            TimeoutPolicy timeoutPolicy,
            IdempotencyManager idempotencyManager,
            UnifiedLogger logger,
            TracePropagator tracePropagator) {
        this.circuitBreaker      = circuitBreaker;
        this.retryStrategy       = retryStrategy;
        this.timeoutPolicy       = timeoutPolicy;
        this.idempotencyManager  = idempotencyManager;
        this.logger              = logger;
        this.tracePropagator     = tracePropagator;
    }

    /**
     * Executes an integration call through the full resilience pipeline.
     *
     * @param request the integration request containing the operation and metadata
     * @param <T>     the return type
     * @return an {@link IntegrationResponse} wrapping the result and execution metadata
     * @throws Exception if all resilience mechanisms are exhausted
     */
    public <T> IntegrationResponse<T> execute(IntegrationRequest<T> request) throws Exception {
        final String serviceId = request.getServiceId();
        final long startTime   = System.currentTimeMillis();

        // ── Step 1: Idempotency check ──────────────────────────────────────
        if (idempotencyManager.isAlreadyProcessed(request.getIdempotencyKey())) {
            String cachedTraceId = tracePropagator.currentTraceId();
            logger.logIdempotencyHit(cachedTraceId, serviceId, request.getIdempotencyKey());

            @SuppressWarnings("unchecked")
            T cachedResult = (T) idempotencyManager.executeIfNew(request.getIdempotencyKey(), () -> null);

            return IntegrationResponse.builder(cachedResult)
                    .traceId(cachedTraceId)
                    .durationMs(0)
                    .fromCache(true)
                    .build();
        }

        // ── Step 2: Start trace span + inject context ──────────────────────
        Map<String, String> outgoingHeaders = new HashMap<>(request.getHeaders());
        String spanId = tracePropagator.startSpan(request.getOperationName(), serviceId);
        tracePropagator.injectContext(outgoingHeaders);
        String traceId = tracePropagator.currentTraceId();

        // ── Step 3: Log request start ──────────────────────────────────────
        logger.logRequest(traceId, serviceId, request.getIdempotencyKey());

        boolean success = false;
        try {
            // ── Steps 4–6: CircuitBreaker → Retry → Timeout → Operation ───
            T result = executeWithResilience(request, serviceId);

            // ── Step 7: Record success + cache for idempotency ────────────
            success = true;
            long duration = System.currentTimeMillis() - startTime;

            idempotencyManager.executeIfNew(request.getIdempotencyKey(), () -> result);
            logger.logSuccess(traceId, serviceId, duration);

            return IntegrationResponse.builder(result)
                    .traceId(traceId)
                    .durationMs(duration)
                    .fromCache(false)
                    .build();

        } catch (Exception ex) {
            // ── Step 8: Log failure ────────────────────────────────────────
            long duration = System.currentTimeMillis() - startTime;
            logger.logFailure(traceId, serviceId, duration, ex);
            throw ex;

        } finally {
            // ── Step 9: Always close the span ─────────────────────────────
            tracePropagator.endSpan(spanId, success);
        }
    }

    /**
     * Composes CircuitBreaker → RetryStrategy → TimeoutPolicy around the operation.
     *
     * <p>The order is intentional:
     * <ul>
     *   <li>CircuitBreaker is outermost — fail-fast before even starting a retry loop.</li>
     *   <li>RetryStrategy wraps TimeoutPolicy — each retry attempt gets its own fresh timeout.</li>
     *   <li>TimeoutPolicy is innermost — closest to the actual call.</li>
     * </ul>
     */
    private <T> T executeWithResilience(IntegrationRequest<T> request, String serviceId) throws Exception {
        return circuitBreaker.execute(serviceId, () -> {
            if (request.isRetryEnabled()) {
                return retryStrategy.execute(serviceId, () ->
                        timeoutPolicy.executeWithTimeout(serviceId, request.getOperation())
                );
            } else {
                return timeoutPolicy.executeWithTimeout(serviceId, request.getOperation());
            }
        });
    }
}
