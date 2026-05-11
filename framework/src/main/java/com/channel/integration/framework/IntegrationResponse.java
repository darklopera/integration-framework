package com.channel.integration.framework;

/**
 * Wraps the result of an integration call with execution metadata.
 *
 * @param <T> the return type of the integration call
 */
public class IntegrationResponse<T> {

    private final T result;
    private final String traceId;
    private final long durationMs;
    private final boolean fromCache;
    private final int totalAttempts;

    private IntegrationResponse(Builder<T> builder) {
        this.result        = builder.result;
        this.traceId       = builder.traceId;
        this.durationMs    = builder.durationMs;
        this.fromCache     = builder.fromCache;
        this.totalAttempts = builder.totalAttempts;
    }

    public T getResult()         { return result; }
    public String getTraceId()   { return traceId; }
    public long getDurationMs()  { return durationMs; }
    public boolean isFromCache() { return fromCache; }
    public int getTotalAttempts(){ return totalAttempts; }

    @Override
    public String toString() {
        return "IntegrationResponse{traceId='%s', durationMs=%d, fromCache=%b, attempts=%d}"
                .formatted(traceId, durationMs, fromCache, totalAttempts);
    }

    public static <T> Builder<T> builder(T result) {
        return new Builder<>(result);
    }

    public static class Builder<T> {
        private final T result;
        private String traceId = "";
        private long durationMs = 0;
        private boolean fromCache = false;
        private int totalAttempts = 1;

        public Builder(T result) { this.result = result; }

        public Builder<T> traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder<T> durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder<T> fromCache(boolean fromCache) { this.fromCache = fromCache; return this; }
        public Builder<T> totalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; return this; }

        public IntegrationResponse<T> build() { return new IntegrationResponse<>(this); }
    }
}
