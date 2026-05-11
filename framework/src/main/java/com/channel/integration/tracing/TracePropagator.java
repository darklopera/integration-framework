package com.channel.integration.tracing;

import java.util.Map;

/**
 * Contract for W3C TraceContext propagation (OpenTelemetry standard).
 *
 * <p>The framework uses this to inject trace context into outgoing calls
 * (so downstream services can continue the same trace) and to extract
 * context from incoming calls (so the framework continues an existing trace).</p>
 */
public interface TracePropagator {

    /**
     * Starts a new span for the given operation.
     *
     * @param operationName human-readable name of the operation
     * @param serviceId     downstream service being called
     * @return spanId to pass to {@link #endSpan}
     */
    String startSpan(String operationName, String serviceId);

    /**
     * Injects the current trace context into the given carrier (e.g., HTTP headers).
     * This propagates the trace to the downstream service.
     *
     * @param carrier mutable map representing outgoing headers
     */
    void injectContext(Map<String, String> carrier);

    /**
     * Extracts a trace context from an incoming carrier and sets it as current.
     *
     * @param carrier map of incoming headers
     * @return the extracted traceId, or a new one if not present
     */
    String extractContext(Map<String, String> carrier);

    /**
     * Ends the span, recording success or failure.
     *
     * @param spanId  the span identifier returned by {@link #startSpan}
     * @param success whether the call succeeded
     */
    void endSpan(String spanId, boolean success);

    /**
     * Returns the current trace ID, or null if no active trace.
     */
    String currentTraceId();
}
