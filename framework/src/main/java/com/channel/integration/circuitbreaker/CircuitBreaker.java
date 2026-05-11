package com.channel.integration.circuitbreaker;

import java.util.concurrent.Callable;

/**
 * Circuit Breaker contract.
 *
 * <p>Dependency Inversion Principle: the framework depends on this abstraction,
 * not on any concrete implementation. Teams can provide their own implementation
 * (e.g., backed by Redis for distributed state) without changing the framework.</p>
 *
 * <p>Interface Segregation Principle: this interface exposes only the methods
 * callers need. State inspection and reset are separate concerns kept minimal.</p>
 */
public interface CircuitBreaker {

    /**
     * Executes the given callable under circuit-breaker protection.
     *
     * <p>If the breaker is OPEN, throws {@link com.channel.integration.exception.CircuitBreakerOpenException}
     * immediately without calling the callable (fail-fast).</p>
     *
     * @param serviceId unique identifier of the downstream service
     * @param callable  the operation to execute
     * @param <T>       return type of the operation
     * @return the result of the callable
     * @throws Exception if the callable fails or the breaker is OPEN
     */
    <T> T execute(String serviceId, Callable<T> callable) throws Exception;

    /**
     * Returns the current state of the breaker for the given serviceId.
     */
    CircuitBreakerState getState(String serviceId);

    /**
     * Manually resets the circuit breaker to CLOSED state.
     * Useful for operational runbooks after a confirmed fix.
     */
    void reset(String serviceId);
}
