package com.channel.demo.service;

import com.channel.integration.circuitbreaker.CircuitBreaker;
import com.channel.integration.framework.IntegrationFramework;
import com.channel.integration.framework.IntegrationRequest;
import com.channel.integration.framework.IntegrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application service that processes payment requests using the Integration Framework.
 *
 * <p>This is the primary demo component for Section C. It demonstrates how a backend
 * service uses the {@link IntegrationFramework} to call a flaky upstream with full
 * resilience: circuit breaker, retry, timeout, and idempotency.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String SERVICE_ID = "payments-core";

    private final IntegrationFramework framework;
    private final FlakyUpstreamService flakyUpstream;
    private final CircuitBreaker circuitBreaker;

    public PaymentService(IntegrationFramework framework,
                          FlakyUpstreamService flakyUpstream,
                          CircuitBreaker circuitBreaker) {
        this.framework      = framework;
        this.flakyUpstream  = flakyUpstream;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Processes a payment through the Integration Framework pipeline.
     *
     * <p>The idempotency key ensures that if the client retries this request
     * (e.g., due to a timeout), the payment is only processed once.</p>
     *
     * @param paymentId      unique payment identifier
     * @param idempotencyKey client-provided key for safe retries (e.g., UUID)
     * @return payment confirmation string
     */
    public IntegrationResponse<String> processPayment(String paymentId, String idempotencyKey) throws Exception {
        log.info("[PaymentService] Processing payment paymentId={} idempotencyKey={}", paymentId, idempotencyKey);

        IntegrationRequest<String> request = IntegrationRequest
                .<String>builder(SERVICE_ID, () -> flakyUpstream.processPayment(paymentId))
                .operationName("processPayment")
                .idempotencyKey(idempotencyKey)
                .header("X-Payment-Id", paymentId)
                .build();

        return framework.execute(request);
    }

    /** Returns the current circuit breaker state for the payments-core service. */
    public String getCircuitBreakerState() {
        return circuitBreaker.getState(SERVICE_ID).name();
    }

    /** Manually resets the circuit breaker (for demo / runbook purposes). */
    public void resetCircuitBreaker() {
        circuitBreaker.reset(SERVICE_ID);
    }
}
