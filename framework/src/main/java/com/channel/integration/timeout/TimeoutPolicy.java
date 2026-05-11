package com.channel.integration.timeout;

import java.util.concurrent.Callable;

/**
 * Contract for enforcing call timeouts.
 *
 * <p>Interface Segregation: timeout enforcement is a separate concern from
 * retry logic and circuit breaking. Each is composable independently.</p>
 */
public interface TimeoutPolicy {

    /**
     * Executes the callable, enforcing the configured timeout for the given serviceId.
     *
     * @param serviceId identifier used to look up the per-service timeout override
     * @param callable  the operation to execute
     * @param <T>       return type
     * @return the result of the callable
     * @throws com.channel.integration.exception.CallTimeoutException if timeout is exceeded
     * @throws Exception if the callable itself throws
     */
    <T> T executeWithTimeout(String serviceId, Callable<T> callable) throws Exception;
}
