package com.channel.integration.logging;

import com.channel.integration.circuitbreaker.CircuitBreakerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Structured JSON logger for the Integration Framework.
 *
 * <p>Uses SLF4J with MDC (Mapped Diagnostic Context) to inject correlation fields
 * into every log entry. Combined with the Logstash JSON encoder, every log line
 * is a machine-readable JSON object containing:</p>
 * <ul>
 *   <li>{@code traceId} — OpenTelemetry trace identifier</li>
 *   <li>{@code serviceId} — downstream service being called</li>
 *   <li>{@code durationMs} — call duration for SLO analysis</li>
 *   <li>{@code event} — categorized event type for dashboards</li>
 *   <li>{@code cbState} — circuit breaker state (when applicable)</li>
 * </ul>
 *
 * <p>The Observability Platform ingests these fields to power dashboards,
 * SLO burn-rate alerts, and root cause analysis.</p>
 */
@Component
public class StructuredLogger implements UnifiedLogger {

    private static final Logger log = LoggerFactory.getLogger("integration.framework");

    // MDC key constants — ensure consistent field names across all log entries
    private static final String MDC_TRACE_ID    = "traceId";
    private static final String MDC_SERVICE_ID  = "serviceId";
    private static final String MDC_EVENT       = "event";
    private static final String MDC_DURATION_MS = "durationMs";
    private static final String MDC_CB_STATE    = "cbState";
    private static final String MDC_ATTEMPT     = "retryAttempt";
    private static final String MDC_IDEM_KEY    = "idempotencyKey";

    @Override
    public void logRequest(String traceId, String serviceId, String idempotencyKey) {
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SERVICE_ID, serviceId);
            MDC.put(MDC_EVENT, "INTEGRATION_REQUEST");
            if (idempotencyKey != null) MDC.put(MDC_IDEM_KEY, idempotencyKey);
            log.info("[Framework] → Calling serviceId={} traceId={}", serviceId, traceId);
        } finally {
            clearMdc(MDC_TRACE_ID, MDC_SERVICE_ID, MDC_EVENT, MDC_IDEM_KEY);
        }
    }

    @Override
    public void logSuccess(String traceId, String serviceId, long durationMs) {
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SERVICE_ID, serviceId);
            MDC.put(MDC_EVENT, "INTEGRATION_SUCCESS");
            MDC.put(MDC_DURATION_MS, String.valueOf(durationMs));
            log.info("[Framework] ✓ SUCCESS serviceId={} duration={}ms traceId={}", serviceId, durationMs, traceId);
        } finally {
            clearMdc(MDC_TRACE_ID, MDC_SERVICE_ID, MDC_EVENT, MDC_DURATION_MS);
        }
    }

    @Override
    public void logFailure(String traceId, String serviceId, long durationMs, Throwable error) {
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SERVICE_ID, serviceId);
            MDC.put(MDC_EVENT, "INTEGRATION_FAILURE");
            MDC.put(MDC_DURATION_MS, String.valueOf(durationMs));
            log.error("[Framework] ✗ FAILURE serviceId={} duration={}ms error={} traceId={}",
                      serviceId, durationMs, error.getMessage(), traceId, error);
        } finally {
            clearMdc(MDC_TRACE_ID, MDC_SERVICE_ID, MDC_EVENT, MDC_DURATION_MS);
        }
    }

    @Override
    public void logRetryAttempt(String traceId, String serviceId, int attempt, int maxAttempts, long delayMs) {
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SERVICE_ID, serviceId);
            MDC.put(MDC_EVENT, "RETRY_ATTEMPT");
            MDC.put(MDC_ATTEMPT, attempt + "/" + maxAttempts);
            log.warn("[Framework] ↺ RETRY {}/{} serviceId={} nextDelayMs={} traceId={}",
                     attempt, maxAttempts, serviceId, delayMs, traceId);
        } finally {
            clearMdc(MDC_TRACE_ID, MDC_SERVICE_ID, MDC_EVENT, MDC_ATTEMPT);
        }
    }

    @Override
    public void logCircuitBreakerStateChange(String serviceId, CircuitBreakerState from, CircuitBreakerState to) {
        try {
            MDC.put(MDC_SERVICE_ID, serviceId);
            MDC.put(MDC_EVENT, "CIRCUIT_BREAKER_STATE_CHANGE");
            MDC.put(MDC_CB_STATE, to.name());
            log.warn("[Framework] ⚡ CircuitBreaker {} → {} for serviceId={}", from, to, serviceId);
        } finally {
            clearMdc(MDC_SERVICE_ID, MDC_EVENT, MDC_CB_STATE);
        }
    }

    @Override
    public void logIdempotencyHit(String traceId, String serviceId, String idempotencyKey) {
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SERVICE_ID, serviceId);
            MDC.put(MDC_EVENT, "IDEMPOTENCY_HIT");
            MDC.put(MDC_IDEM_KEY, idempotencyKey);
            log.info("[Framework] 🔑 IDEMPOTENCY HIT key={} serviceId={} — returning cached result", idempotencyKey, serviceId);
        } finally {
            clearMdc(MDC_TRACE_ID, MDC_SERVICE_ID, MDC_EVENT, MDC_IDEM_KEY);
        }
    }

    private void clearMdc(String... keys) {
        for (String key : keys) MDC.remove(key);
    }
}
