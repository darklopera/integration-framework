package com.channel.integration;

import com.channel.integration.config.IntegrationProperties;
import com.channel.integration.idempotency.IdempotencyManager;
import com.channel.integration.idempotency.InMemoryIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("IdempotencyManager")
class IdempotencyTest {

    private IdempotencyManager idempotencyManager;

    @BeforeEach
    void setUp() {
        IntegrationProperties props = new IntegrationProperties();
        props.getIdempotency().setTtlMs(60_000);
        idempotencyManager = new IdempotencyManager(new InMemoryIdempotencyStore(), props);
    }

    @Test
    @DisplayName("Should execute operation on first call")
    void shouldExecuteOnFirstCall() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        String result = idempotencyManager.executeIfNew("key-001", () -> {
            callCount.incrementAndGet();
            return "payment-processed";
        });
        assertThat(result).isEqualTo("payment-processed");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return cached result on second call with same key")
    void shouldReturnCachedOnDuplicateKey() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        // First call — executes
        idempotencyManager.executeIfNew("key-002", () -> {
            callCount.incrementAndGet();
            return "first-response";
        });

        // Second call with same key — should NOT execute
        String result = idempotencyManager.executeIfNew("key-002", () -> {
            callCount.incrementAndGet();
            return "second-response";
        });

        assertThat(result).isEqualTo("first-response"); // returns cached
        assertThat(callCount.get()).isEqualTo(1);        // only executed once
    }

    @Test
    @DisplayName("Should execute when no idempotency key provided")
    void shouldExecuteWithoutKey() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        idempotencyManager.executeIfNew(null, () -> { callCount.incrementAndGet(); return "ok"; });
        idempotencyManager.executeIfNew(null, () -> { callCount.incrementAndGet(); return "ok"; });

        assertThat(callCount.get()).isEqualTo(2); // executed both times
    }

    @Test
    @DisplayName("Should correctly report already-processed keys")
    void shouldDetectAlreadyProcessedKeys() throws Exception {
        assertThat(idempotencyManager.isAlreadyProcessed("new-key")).isFalse();

        idempotencyManager.executeIfNew("new-key", () -> "result");

        assertThat(idempotencyManager.isAlreadyProcessed("new-key")).isTrue();
    }
}
