package com.channel.integration.exception;

/**
 * Thrown when a call is rejected because the Circuit Breaker is in OPEN state.
 * This is a fast-fail — no network call is attempted.
 */
public class CircuitBreakerOpenException extends IntegrationException {

    public CircuitBreakerOpenException(String serviceId) {
        super(serviceId, "CIRCUIT_BREAKER_OPEN",
              String.format("Circuit breaker is OPEN for service [%s]. Call rejected to protect the system.", serviceId));
    }
}
