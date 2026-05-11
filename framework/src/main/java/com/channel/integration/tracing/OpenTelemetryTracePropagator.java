package com.channel.integration.tracing;

import com.channel.integration.config.IntegrationProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenTelemetry implementation of {@link TracePropagator}.
 *
 * <p>Implements W3C TraceContext (traceparent / tracestate headers) propagation.
 * Every outgoing call gets the current span context injected into its headers,
 * creating a continuous distributed trace visible in the Observability Platform.</p>
 *
 * <p>If the OTel SDK is not configured (e.g., in unit tests without a collector),
 * this class falls back gracefully to generating random traceIds without failing.</p>
 */
@Component
public class OpenTelemetryTracePropagator implements TracePropagator {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryTracePropagator.class);

    private final IntegrationProperties.TracingConfig config;
    private final ConcurrentHashMap<String, Span> activeSpans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Scope> activeScopes = new ConcurrentHashMap<>();

    private Tracer tracer;

    public OpenTelemetryTracePropagator(IntegrationProperties properties) {
        this.config = properties.getTracing();
        initTracer();
    }

    private void initTracer() {
        try {
            this.tracer = GlobalOpenTelemetry.getTracer(config.getServiceName(), "1.0.0");
            log.info("[Tracing] OpenTelemetry tracer initialized for service={}", config.getServiceName());
        } catch (Exception e) {
            log.warn("[Tracing] Could not initialize OTel tracer — tracing will be no-op: {}", e.getMessage());
            this.tracer = null;
        }
    }

    @Override
    public String startSpan(String operationName, String serviceId) {
        if (tracer == null) return generateFallbackTraceId();

        try {
            Span span = tracer.spanBuilder(operationName)
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("service.id", serviceId)
                    .setAttribute("framework.component", "integration-framework")
                    .startSpan();

            Scope scope = span.makeCurrent();
            String spanId = span.getSpanContext().getSpanId();

            activeSpans.put(spanId, span);
            activeScopes.put(spanId, scope);

            log.debug("[Tracing] Span started: operationName={} serviceId={} spanId={} traceId={}",
                      operationName, serviceId, spanId, span.getSpanContext().getTraceId());

            return spanId;

        } catch (Exception e) {
            log.warn("[Tracing] Failed to start span — falling back: {}", e.getMessage());
            return generateFallbackTraceId();
        }
    }

    @Override
    public void injectContext(Map<String, String> carrier) {
        if (tracer == null) return;
        try {
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), carrier, MAP_SETTER);
            log.debug("[Tracing] Context injected into carrier: traceparent={}", carrier.get("traceparent"));
        } catch (Exception e) {
            log.warn("[Tracing] Failed to inject context: {}", e.getMessage());
        }
    }

    @Override
    public String extractContext(Map<String, String> carrier) {
        if (tracer == null) return generateFallbackTraceId();
        try {
            Context extracted = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                    .extract(Context.current(), carrier, MAP_GETTER);
            Span span = Span.fromContext(extracted);
            String traceId = span.getSpanContext().getTraceId();
            log.debug("[Tracing] Context extracted from carrier: traceId={}", traceId);
            return traceId.isBlank() ? generateFallbackTraceId() : traceId;
        } catch (Exception e) {
            log.warn("[Tracing] Failed to extract context: {}", e.getMessage());
            return generateFallbackTraceId();
        }
    }

    @Override
    public void endSpan(String spanId, boolean success) {
        Span span = activeSpans.remove(spanId);
        Scope scope = activeScopes.remove(spanId);

        if (span == null) return;

        try {
            if (success) {
                span.setStatus(StatusCode.OK);
            } else {
                span.setStatus(StatusCode.ERROR, "Integration call failed");
            }
        } finally {
            if (scope != null) scope.close();
            span.end();
            log.debug("[Tracing] Span ended: spanId={} success={}", spanId, success);
        }
    }

    @Override
    public String currentTraceId() {
        if (tracer == null) return null;
        try {
            return Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            return null;
        }
    }

    private String generateFallbackTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ── OTel TextMap adapters ─────────────────────────────────────────────

    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> { if (carrier != null) carrier.put(key, value); };

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        @Nullable
        public String get(@Nullable Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };
}
