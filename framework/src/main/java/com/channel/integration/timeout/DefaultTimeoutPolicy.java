package com.channel.integration.timeout;

import com.channel.integration.config.IntegrationProperties;
import com.channel.integration.exception.CallTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Timeout enforcement using {@link CompletableFuture} with a dedicated executor.
 *
 * <p>Each call is submitted to a cached thread pool. If the call does not complete
 * within the configured timeout, a {@link CallTimeoutException} is thrown and the
 * underlying thread is interrupted. This prevents slow downstream services from
 * consuming threads indefinitely (bulkhead protection at the thread level).</p>
 */
@Component
public class DefaultTimeoutPolicy implements TimeoutPolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultTimeoutPolicy.class);

    private final IntegrationProperties.TimeoutConfig config;
    private final ExecutorService executor;

    public DefaultTimeoutPolicy(IntegrationProperties properties) {
        this.config = properties.getTimeout();
        // Virtual threads (Java 21+) or cached pool for I/O-bound calls
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "integration-timeout-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public <T> T executeWithTimeout(String serviceId, Callable<T> callable) throws Exception {
        long timeoutMs = config.getEffectiveTimeoutMs(serviceId);

        log.debug("[Timeout] Executing with timeout={}ms for serviceId={}", timeoutMs, serviceId);

        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            future.cancel(true); // Interrupt the underlying thread
            log.error("[Timeout] EXCEEDED {}ms for serviceId={}", timeoutMs, serviceId);
            throw new CallTimeoutException(serviceId, timeoutMs);

        } catch (ExecutionException e) {
            // Unwrap the original exception thrown by the callable
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new CallTimeoutException(serviceId, timeoutMs);
        }
    }
}
