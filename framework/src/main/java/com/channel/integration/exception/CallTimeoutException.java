package com.channel.integration.exception;

/**
 * Thrown when a service call exceeds the configured timeout.
 */
public class CallTimeoutException extends IntegrationException {

    private final long timeoutMs;

    public CallTimeoutException(String serviceId, long timeoutMs) {
        super(serviceId, "CALL_TIMEOUT",
              String.format("Call to service [%s] timed out after %dms.", serviceId, timeoutMs));
        this.timeoutMs = timeoutMs;
    }

    public long getTimeoutMs() { return timeoutMs; }
}
