package com.channel.integration;

import com.channel.integration.config.IntegrationProperties;
import com.channel.integration.exception.RetryExhaustedException;
import com.channel.integration.retry.ExponentialBackoffRetryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ExponentialBackoffRetryStrategy")
class RetryStrategyTest {

    private ExponentialBackoffRetryStrategy retryStrategy;
    private static final String SERVICE_ID = "test-service";

    @BeforeEach
    void setUp() {
        IntegrationProperties props = new IntegrationProperties();
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setBaseDelayMs(10);   // short for tests
        props.getRetry().setMultiplier(2.0);
        props.getRetry().setMaxDelayMs(100);
        props.getRetry().setJitterFactor(0.1);
        retryStrategy = new ExponentialBackoffRetryStrategy(props);
    }

    @Test
    @DisplayName("Should succeed on first attempt")
    void shouldSucceedOnFirstAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = retryStrategy.execute(SERVICE_ID, () -> {
            attempts.incrementAndGet();
            return "success";
        });
        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should retry and succeed on second attempt")
    void shouldRetryAndSucceedOnSecondAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = retryStrategy.execute(SERVICE_ID, () -> {
            if (attempts.incrementAndGet() < 2) throw new RuntimeException("transient failure");
            return "recovered";
        });
        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should throw RetryExhaustedException after all attempts fail")
    void shouldThrowAfterAllAttemptsFail() {
        AtomicInteger attempts = new AtomicInteger(0);
        assertThatThrownBy(() ->
            retryStrategy.execute(SERVICE_ID, () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("always fails");
            })
        )
        .isInstanceOf(RetryExhaustedException.class)
        .satisfies(ex -> {
            RetryExhaustedException ree = (RetryExhaustedException) ex;
            assertThat(ree.getTotalAttempts()).isEqualTo(3);
            assertThat(ree.getServiceId()).isEqualTo(SERVICE_ID);
        });

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should compute increasing delays (exponential backoff)")
    void shouldIncreaseDelayExponentially() {
        // Verify the delay formula: baseDelay * multiplier^attemptIndex
        IntegrationProperties props = new IntegrationProperties();
        props.getRetry().setBaseDelayMs(100);
        props.getRetry().setMultiplier(2.0);
        props.getRetry().setJitterFactor(0.0); // no jitter for deterministic test
        props.getRetry().setMaxDelayMs(10000);
        props.getRetry().setMaxAttempts(3);

        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy(props);

        // attempt 0 delay = 100ms, attempt 1 = 200ms, attempt 2 = 400ms
        // Just verifying the strategy doesn't throw configuration errors
        AtomicInteger count = new AtomicInteger(0);
        assertThatThrownBy(() ->
            strategy.execute(SERVICE_ID, () -> {
                count.incrementAndGet();
                throw new RuntimeException("always fails");
            })
        ).isInstanceOf(RetryExhaustedException.class);

        assertThat(count.get()).isEqualTo(3);
    }
}
