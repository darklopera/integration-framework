package com.channel.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates a flaky upstream service for Section C of the Technical Assessment.
 *
 * <p>Behavior modes configurable at runtime:</p>
 * <ul>
 *   <li><strong>RANDOM_FAILURE</strong> — fails ~50% of calls (default)</li>
 *   <li><strong>ALWAYS_FAIL</strong>   — all calls fail (to demonstrate circuit breaker)</li>
 *   <li><strong>SLOW_RESPONSE</strong> — responds after 8 seconds (to demonstrate timeout)</li>
 *   <li><strong>HEALTHY</strong>       — all calls succeed (recovery mode)</li>
 * </ul>
 */
@Service
public class FlakyUpstreamService {

    private static final Logger log = LoggerFactory.getLogger(FlakyUpstreamService.class);
    private static final Random RANDOM = new Random();

    public enum Mode { RANDOM_FAILURE, ALWAYS_FAIL, SLOW_RESPONSE, HEALTHY }

    private volatile Mode mode = Mode.RANDOM_FAILURE;
    private final AtomicInteger totalCalls   = new AtomicInteger(0);
    private final AtomicInteger totalSuccess = new AtomicInteger(0);
    private final AtomicInteger totalFailure = new AtomicInteger(0);

    /**
     * Simulates calling a downstream payment processing service.
     *
     * @param paymentId the payment identifier
     * @return a payment confirmation string
     * @throws RuntimeException when the simulated service fails
     */
    public String processPayment(String paymentId) throws Exception {
        int call = totalCalls.incrementAndGet();
        log.info("[FlakyUpstream] Received payment request #{} for paymentId={} mode={}", call, paymentId, mode);

        return switch (mode) {
            case ALWAYS_FAIL -> {
                totalFailure.incrementAndGet();
                throw new RuntimeException(
                    "[FlakyUpstream] Payment service unavailable (ALWAYS_FAIL mode) for paymentId=" + paymentId);
            }
            case SLOW_RESPONSE -> {
                log.warn("[FlakyUpstream] Simulating slow response (8s) for paymentId={}", paymentId);
                Thread.sleep(8_000);
                totalSuccess.incrementAndGet();
                yield "PAYMENT_CONFIRMED_SLOW:" + paymentId;
            }
            case RANDOM_FAILURE -> {
                if (RANDOM.nextDouble() < 0.5) {
                    totalFailure.incrementAndGet();
                    throw new RuntimeException(
                        "[FlakyUpstream] Random transient failure for paymentId=" + paymentId);
                }
                totalSuccess.incrementAndGet();
                yield "PAYMENT_CONFIRMED:" + paymentId;
            }
            case HEALTHY -> {
                totalSuccess.incrementAndGet();
                yield "PAYMENT_CONFIRMED:" + paymentId;
            }
        };
    }

    /** Changes the simulation mode at runtime (for demo purposes). */
    public void setMode(Mode mode) {
        log.info("[FlakyUpstream] Mode changed: {} → {}", this.mode, mode);
        this.mode = mode;
    }

    public Mode getMode() { return mode; }

    /** Returns a snapshot of call statistics. */
    public String getStats() {
        return "FlakyUpstream Stats | total=%d success=%d failure=%d mode=%s"
                .formatted(totalCalls.get(), totalSuccess.get(), totalFailure.get(), mode);
    }
}
