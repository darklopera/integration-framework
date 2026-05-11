package com.channel.integration.exception;

/**
 * Thrown when all retry attempts have been exhausted and the operation still fails.
 */
public class RetryExhaustedException extends IntegrationException {

    private final int totalAttempts;

    public RetryExhaustedException(String serviceId, int totalAttempts, Throwable lastCause) {
        super(serviceId, "RETRY_EXHAUSTED",
              String.format("All %d retry attempts exhausted for service [%s]. Last error: %s",
                            totalAttempts, serviceId, lastCause.getMessage()),
              lastCause);
        this.totalAttempts = totalAttempts;
    }

    public int getTotalAttempts() { return totalAttempts; }
}
