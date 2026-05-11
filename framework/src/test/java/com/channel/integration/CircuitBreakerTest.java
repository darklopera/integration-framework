package com.channel.integration;

import com.channel.integration.circuitbreaker.CircuitBreakerState;
import com.channel.integration.circuitbreaker.DefaultCircuitBreaker;
import com.channel.integration.config.IntegrationProperties;
import com.channel.integration.exception.CircuitBreakerOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DefaultCircuitBreaker")
class CircuitBreakerTest {

    private DefaultCircuitBreaker circuitBreaker;
    private static final String SERVICE_ID = "test-service";

    @BeforeEach
    void setUp() {
        IntegrationProperties props = new IntegrationProperties();
        props.getCircuitBreaker().setErrorThresholdPercent(50.0);
        props.getCircuitBreaker().setWindowSizeMs(10000);
        props.getCircuitBreaker().setMinimumCallsInWindow(4);
        props.getCircuitBreaker().setHalfOpenMaxCalls(2);
        props.getCircuitBreaker().setResetTimeoutMs(100); // short for tests
        circuitBreaker = new DefaultCircuitBreaker(props);
    }

    @Test
    @DisplayName("Should start in CLOSED state")
    void shouldStartClosed() {
        assertThat(circuitBreaker.getState(SERVICE_ID)).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    @DisplayName("Should allow calls when CLOSED")
    void shouldAllowCallsWhenClosed() throws Exception {
        String result = circuitBreaker.execute(SERVICE_ID, () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("Should trip to OPEN when error threshold exceeded")
    void shouldTripWhenThresholdExceeded() {
        // Fill window with failures (4 calls, all fail → 100% error rate > 50%)
        for (int i = 0; i < 4; i++) {
            try {
                circuitBreaker.execute(SERVICE_ID, () -> { throw new RuntimeException("fail"); });
            } catch (Exception ignored) {}
        }

        assertThat(circuitBreaker.getState(SERVICE_ID)).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    @DisplayName("Should reject calls immediately when OPEN (fail-fast)")
    void shouldRejectWhenOpen() {
        // Trip the breaker
        for (int i = 0; i < 4; i++) {
            try { circuitBreaker.execute(SERVICE_ID, () -> { throw new RuntimeException(); }); }
            catch (Exception ignored) {}
        }

        // Now it should fail-fast without calling the operation
        assertThatThrownBy(() -> circuitBreaker.execute(SERVICE_ID, () -> "never-called"))
                .isInstanceOf(CircuitBreakerOpenException.class)
                .hasMessageContaining(SERVICE_ID);
    }

    @Test
    @DisplayName("Should transition to HALF_OPEN after reset timeout")
    void shouldTransitionToHalfOpen() throws Exception {
        // Trip the breaker
        for (int i = 0; i < 4; i++) {
            try { circuitBreaker.execute(SERVICE_ID, () -> { throw new RuntimeException(); }); }
            catch (Exception ignored) {}
        }

        // Wait for reset timeout (100ms in tests)
        Thread.sleep(150);

        assertThat(circuitBreaker.getState(SERVICE_ID)).isEqualTo(CircuitBreakerState.HALF_OPEN);
    }

    @Test
    @DisplayName("Should recover to CLOSED after successful probes in HALF_OPEN")
    void shouldRecoverAfterSuccessfulProbes() throws Exception {
        // Trip the breaker
        for (int i = 0; i < 4; i++) {
            try { circuitBreaker.execute(SERVICE_ID, () -> { throw new RuntimeException(); }); }
            catch (Exception ignored) {}
        }

        Thread.sleep(150); // wait for HALF_OPEN

        // Send successful probe calls
        circuitBreaker.execute(SERVICE_ID, () -> "probe-ok");
        circuitBreaker.execute(SERVICE_ID, () -> "probe-ok");

        assertThat(circuitBreaker.getState(SERVICE_ID)).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    @DisplayName("Should reset manually")
    void shouldResetManually() {
        for (int i = 0; i < 4; i++) {
            try { circuitBreaker.execute(SERVICE_ID, () -> { throw new RuntimeException(); }); }
            catch (Exception ignored) {}
        }

        circuitBreaker.reset(SERVICE_ID);
        assertThat(circuitBreaker.getState(SERVICE_ID)).isEqualTo(CircuitBreakerState.CLOSED);
    }
}
