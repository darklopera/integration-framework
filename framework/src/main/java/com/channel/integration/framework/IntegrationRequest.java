package com.channel.integration.framework;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Encapsulates all information needed to execute an integration call.
 *
 * <p>Using a request object (vs. multiple method parameters) adheres to the
 * Open/Closed Principle: adding new optional parameters (e.g., priority, metadata)
 * doesn't change existing method signatures.</p>
 *
 * @param <T> the expected return type of the integration call
 */
public class IntegrationRequest<T> {

    private final String serviceId;
    private final String operationName;
    private final Callable<T> operation;
    private final String idempotencyKey;
    private final Map<String, String> headers;
    private final boolean retryEnabled;

    private IntegrationRequest(Builder<T> builder) {
        this.serviceId      = builder.serviceId;
        this.operationName  = builder.operationName;
        this.operation      = builder.operation;
        this.idempotencyKey = builder.idempotencyKey;
        this.headers        = Map.copyOf(builder.headers);
        this.retryEnabled   = builder.retryEnabled;
    }

    public String getServiceId()      { return serviceId; }
    public String getOperationName()  { return operationName; }
    public Callable<T> getOperation() { return operation; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Map<String, String> getHeaders() { return headers; }
    public boolean isRetryEnabled()   { return retryEnabled; }

    // ── Builder ───────────────────────────────────────────────────────────

    public static <T> Builder<T> builder(String serviceId, Callable<T> operation) {
        return new Builder<>(serviceId, operation);
    }

    public static class Builder<T> {
        private final String serviceId;
        private final Callable<T> operation;
        private String operationName = "integration-call";
        private String idempotencyKey = null;
        private final Map<String, String> headers = new HashMap<>();
        private boolean retryEnabled = true;

        private Builder(String serviceId, Callable<T> operation) {
            if (serviceId == null || serviceId.isBlank())
                throw new IllegalArgumentException("serviceId must not be blank");
            if (operation == null)
                throw new IllegalArgumentException("operation must not be null");
            this.serviceId = serviceId;
            this.operation = operation;
        }

        public Builder<T> operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        /** Sets the idempotency key. Null = no deduplication. */
        public Builder<T> idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /** Adds an HTTP header to propagate (e.g., for trace context injection). */
        public Builder<T> header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        /** Disables retry for this specific call (e.g., non-idempotent writes). */
        public Builder<T> noRetry() {
            this.retryEnabled = false;
            return this;
        }

        public IntegrationRequest<T> build() {
            return new IntegrationRequest<>(this);
        }
    }
}
