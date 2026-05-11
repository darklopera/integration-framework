package com.channel.integration.retry;

import java.util.concurrent.Callable;

/**
 * Contract for retry execution strategies.
 *
 * <p>Open/Closed + Liskov: any implementation can be swapped in transparently.
 * For example, a {@code LinearBackoffRetryStrategy} or {@code NoRetryStrategy}
 * (for idempotency-sensitive flows) can replace this without touching the framework.</p>
 */
public interface RetryStrategy {

    /**
     * Executes the callable, retrying on failure according to the strategy's rules.
     *
     * @param serviceId identifier used for logging context
     * @param callable  the operation to execute
     * @param <T>       return type
     * @return the result of the first successful execution
     * @throws Exception if all attempts fail
     */
    <T> T execute(String serviceId, Callable<T> callable) throws Exception;
}
