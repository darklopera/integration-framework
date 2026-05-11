package com.channel.integration.retry;

import com.channel.integration.config.IntegrationProperties;
import com.channel.integration.exception.RetryExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry strategy implementing exponential backoff with full jitter.
 *
 * <p><strong>Delay formula per attempt n (0-indexed):</strong></p>
 * <pre>
 *   rawDelay  = min(baseDelayMs × multiplier^n, maxDelayMs)
 *   jitter    = rawDelay × jitterFactor × random(-1, 1)
 *   finalDelay = rawDelay + jitter
 * </pre>
 *
 * <p>Example with defaults (base=500ms, multiplier=2, jitter=20%):</p>
 * <pre>
 *   Attempt 1 → ~500ms ± 100ms
 *   Attempt 2 → ~1000ms ± 200ms
 *   Attempt 3 → ~2000ms ± 400ms
 * </pre>
 *
 * <p><strong>Why jitter?</strong> Without it, all retrying instances sleep for
 * exactly the same duration and then hammer the downstream simultaneously —
 * the "thundering herd" problem. Jitter spreads the load over time.</p>
 */
@Component
public class ExponentialBackoffRetryStrategy implements RetryStrategy {

    private static final Logger log = LoggerFactory.getLogger(ExponentialBackoffRetryStrategy.class);

    private final IntegrationProperties.RetryConfig config;

    public ExponentialBackoffRetryStrategy(IntegrationProperties properties) {
        this.config = properties.getRetry();
    }

    @Override
    public <T> T execute(String serviceId, Callable<T> callable) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= config.getMaxAttempts(); attempt++) {
            try {
                T result = callable.call();
                if (attempt > 1) {
                    log.info("[Retry] SUCCESS on attempt {}/{} for serviceId={}", attempt, config.getMaxAttempts(), serviceId);
                }
                return result;

            } catch (Exception ex) {
                lastException = ex;
                log.warn("[Retry] Attempt {}/{} FAILED for serviceId={} — reason: {}",
                         attempt, config.getMaxAttempts(), serviceId, ex.getMessage());

                if (attempt < config.getMaxAttempts()) {
                    long delay = computeDelay(attempt - 1);
                    log.debug("[Retry] Waiting {}ms before next attempt for serviceId={}", delay, serviceId);
                    sleep(delay);
                }
            }
        }

        log.error("[Retry] All {} attempts exhausted for serviceId={}", config.getMaxAttempts(), serviceId);
        throw new RetryExhaustedException(serviceId, config.getMaxAttempts(), lastException);
    }

    /**
     * Computes the delay for a given attempt index using exponential backoff + jitter.
     *
     * @param attemptIndex zero-based index (0 = delay before 2nd call)
     * @return delay in milliseconds
     */
    private long computeDelay(int attemptIndex) {
        double rawDelay = config.getBaseDelayMs() * Math.pow(config.getMultiplier(), attemptIndex);
        rawDelay = Math.min(rawDelay, config.getMaxDelayMs());

        // Full jitter: random value in [-jitterFactor, +jitterFactor] of rawDelay
        double jitter = rawDelay * config.getJitterFactor() * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
        long finalDelay = Math.max(0, (long) (rawDelay + jitter));

        log.debug("[Retry] Computed delay: rawDelay={}ms jitter={}ms finalDelay={}ms",
                  (long) rawDelay, (long) jitter, finalDelay);
        return finalDelay;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Retry] Sleep interrupted during backoff");
        }
    }
}
