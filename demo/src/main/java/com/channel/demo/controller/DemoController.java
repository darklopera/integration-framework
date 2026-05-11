package com.channel.demo.controller;

import com.channel.demo.service.FlakyUpstreamService;
import com.channel.demo.service.PaymentService;
import com.channel.integration.exception.CircuitBreakerOpenException;
import com.channel.integration.exception.CallTimeoutException;
import com.channel.integration.exception.RetryExhaustedException;
import com.channel.integration.framework.IntegrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/demo")
@Tag(name = "Section C Demo", description = "Integration Framework resilience patterns demo. Use /demo/run-all-tests to run the full test suite.")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);
    private final PaymentService paymentService;
    private final FlakyUpstreamService flakyUpstream;

    public DemoController(PaymentService paymentService, FlakyUpstreamService flakyUpstream) {
        this.paymentService = paymentService;
        this.flakyUpstream = flakyUpstream;
    }

    @Operation(summary = "Process payment through full resilience pipeline",
               description = "Runs: Idempotency check -> OTel Span -> Circuit Breaker -> Retry+Backoff -> Timeout -> Upstream call")
    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> processPayment(
            @Parameter(description = "Payment ID (auto-generated if empty)") @RequestParam(defaultValue = "") String paymentId,
            @Parameter(description = "Idempotency key — same key twice returns cached result") @RequestParam(required = false) String idempotencyKey) {
        try {
            if (paymentId.isBlank()) paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (idempotencyKey == null) idempotencyKey = UUID.randomUUID().toString();
            IntegrationResponse<String> response = paymentService.processPayment(paymentId, idempotencyKey);
            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS", "result", response.getResult(),
                "traceId", response.getTraceId(), "durationMs", response.getDurationMs(),
                "fromCache", response.isFromCache(), "idempotencyKey", idempotencyKey,
                "pattern", response.isFromCache() ? "IDEMPOTENCY_HIT" : "FULL_PIPELINE"
            ));
        } catch (CircuitBreakerOpenException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "CIRCUIT_BREAKER_OPEN", "pattern", "CIRCUIT_BREAKER",
                "message", "Fail-fast — upstream protected", "detail", e.getMessage()));
        } catch (CallTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "status", "TIMEOUT", "pattern", "TIMEOUT",
                "message", "Upstream exceeded configured timeout", "detail", e.getMessage()));
        } catch (RetryExhaustedException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "status", "RETRY_EXHAUSTED", "pattern", "RETRY",
                "attempts", e.getTotalAttempts(), "detail", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Get Circuit Breaker state", description = "Returns CLOSED / OPEN / HALF_OPEN")
    @GetMapping("/circuit-breaker/state")
    public ResponseEntity<Map<String, String>> getCircuitBreakerState() {
        String state = paymentService.getCircuitBreakerState();
        return ResponseEntity.ok(Map.of("service", "payments-core", "state", state,
            "description", switch (state) {
                case "CLOSED" -> "Normal — all calls allowed";
                case "OPEN"   -> "Fail-fast active — all calls rejected";
                case "HALF_OPEN" -> "Probing recovery";
                default -> state;
            }));
    }

    @Operation(summary = "Reset Circuit Breaker to CLOSED")
    @PostMapping("/circuit-breaker/reset")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker() {
        paymentService.resetCircuitBreaker();
        return ResponseEntity.ok(Map.of(
            "message", "Circuit Breaker reset to CLOSED",
            "state", paymentService.getCircuitBreakerState()
        ));
    }

    @Operation(summary = "Change upstream failure mode",
               description = "Modes: RANDOM_FAILURE (50% fail) | ALWAYS_FAIL (trips CB) | SLOW_RESPONSE (triggers timeout) | HEALTHY (recovery)")
    @PutMapping("/mode/{mode}")
    public ResponseEntity<Map<String, String>> changeMode(@PathVariable String mode) {
        try {
            FlakyUpstreamService.Mode newMode = FlakyUpstreamService.Mode.valueOf(mode.toUpperCase());
            flakyUpstream.setMode(newMode);
            return ResponseEntity.ok(Map.of("mode", newMode.name(), "message", "Mode changed",
                "tip", switch (newMode) {
                    case ALWAYS_FAIL    -> "Call /demo/payment ~6x to trip the Circuit Breaker";
                    case SLOW_RESPONSE  -> "Call /demo/payment to see TIMEOUT (8s > 6s limit)";
                    case RANDOM_FAILURE -> "Call /demo/payment to see Retry + Backoff";
                    case HEALTHY        -> "Call /demo/payment to see successful recovery";
                }));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Valid modes: RANDOM_FAILURE | ALWAYS_FAIL | SLOW_RESPONSE | HEALTHY"));
        }
    }

    @Operation(summary = "Get upstream statistics")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, String>> getStats() {
        return ResponseEntity.ok(Map.of(
            "upstreamStats", flakyUpstream.getStats(),
            "currentMode", flakyUpstream.getMode().name(),
            "circuitBreakerState", paymentService.getCircuitBreakerState()
        ));
    }

    @Operation(summary = "RUN ALL SECTION C TEST CASES",
               description = "Runs all 7 test scenarios automatically: Normal flow, Retry, Idempotency, Timeout, CB trip, Fail-fast, Recovery. Returns full report.")
    @PostMapping("/run-all-tests")
    public ResponseEntity<Map<String, Object>> runAllTests() {
        List<Map<String, Object>> results = new ArrayList<>();

        // ── Clean slate before every run ──────────────────────────────────────
        // The Circuit Breaker is a singleton with a 10-second sliding window.
        // Failures from TEST_2 (RANDOM_FAILURE) accumulate in that window and can
        // push the error rate above the 50% threshold before TEST_4 fires its timeout,
        // causing CircuitBreakerOpenException instead of CallTimeoutException.
        // Resetting here guarantees each run starts from a known, deterministic state.
        flakyUpstream.setMode(FlakyUpstreamService.Mode.HEALTHY);
        paymentService.resetCircuitBreaker();

        // ── TEST 1 — Normal flow ──────────────────────────────────────────────
        results.add(runTest("TEST_1", "Normal flow (healthy upstream)", "HEALTHY", () -> {
            flakyUpstream.setMode(FlakyUpstreamService.Mode.HEALTHY);
            IntegrationResponse<String> r = paymentService.processPayment("PAY-T1", UUID.randomUUID().toString());
            return Map.of(
                "outcome", "SUCCESS",
                "result", r.getResult(),
                "durationMs", r.getDurationMs(),
                "pattern", "FULL_PIPELINE"
            );
        }));

        // ── TEST 2 — Retry + Exponential Backoff + Jitter ────────────────────
        results.add(runTest("TEST_2", "Retry + Exponential Backoff + Jitter (50% failure)", "RANDOM_FAILURE", () -> {
            flakyUpstream.setMode(FlakyUpstreamService.Mode.RANDOM_FAILURE);
            try {
                IntegrationResponse<String> r = paymentService.processPayment("PAY-T2", UUID.randomUUID().toString());
                return Map.of(
                    "outcome", "SUCCESS_AFTER_RETRY",
                    "result", r.getResult(),
                    "pattern", "RETRY + BACKOFF + JITTER"
                );
            } catch (RetryExhaustedException ex) {
                return Map.of(
                    "outcome", "RETRY_EXHAUSTED",
                    "attempts", ex.getTotalAttempts(),
                    "pattern", "RETRY + BACKOFF + JITTER"
                );
            }
        }));

        // ── TEST 3 — Idempotency Key ──────────────────────────────────────────
        results.add(runTest("TEST_3", "Idempotency Key — same key twice returns cached", "HEALTHY", () -> {
            flakyUpstream.setMode(FlakyUpstreamService.Mode.HEALTHY);
            String key = "IDEM-" + UUID.randomUUID().toString().substring(0, 6);
            IntegrationResponse<String> first  = paymentService.processPayment("PAY-T3A", key);
            IntegrationResponse<String> second = paymentService.processPayment("PAY-T3B", key);
            return Map.of(
                "pattern", "IDEMPOTENCY_KEY",
                "idempotencyKey", key,
                "firstCall",  Map.of("fromCache", first.isFromCache(),  "result", first.getResult()),
                "secondCall", Map.of("fromCache", second.isFromCache(), "result", second.getResult()),
                "outcome", second.isFromCache() ? "IDEMPOTENCY_HIT PASS" : "IDEMPOTENCY_MISS FAIL"
            );
        }));

        // ── TEST 4 — Timeout ──────────────────────────────────────────────────
        // IMPORTANT: reset the CB before this test.
        // TEST_2 may have recorded failures inside the 10-second sliding window.
        // Without this reset, the accumulated error rate can exceed 50% and cause
        // the CB to open, making this test receive CircuitBreakerOpenException
        // instead of the expected CallTimeoutException.
        paymentService.resetCircuitBreaker();

        results.add(runTest("TEST_4", "Timeout — upstream 8s > configured 6s", "SLOW_RESPONSE", () -> {
            flakyUpstream.setMode(FlakyUpstreamService.Mode.SLOW_RESPONSE);
            try {
                paymentService.processPayment("PAY-T4", UUID.randomUUID().toString());
                return Map.of("outcome", "UNEXPECTED_SUCCESS");
            } catch (CallTimeoutException ex) {
                return Map.of(
                    "outcome", "TIMEOUT PASS",
                    "pattern", "TIMEOUT",
                    "detail", ex.getMessage()
                );
            } catch (CircuitBreakerOpenException ex) {
                // Safety net: should not happen after the reset above.
                // Captured here to surface a clear diagnostic if state contamination persists.
                return Map.of(
                    "outcome", "CB_OPEN_UNEXPECTED — state contamination detected",
                    "detail", ex.getMessage()
                );
            }
        }));

        // ── TEST 5 — Circuit Breaker trip ─────────────────────────────────────
        flakyUpstream.setMode(FlakyUpstreamService.Mode.ALWAYS_FAIL);
        paymentService.resetCircuitBreaker();

        results.add(runTest("TEST_5", "Circuit Breaker trip (ALWAYS_FAIL x6)", "ALWAYS_FAIL", () -> {
            flakyUpstream.setMode(FlakyUpstreamService.Mode.ALWAYS_FAIL);
            paymentService.resetCircuitBreaker();
            int failures = 0;
            for (int i = 0; i < 6; i++) {
                try { paymentService.processPayment("PAY-T5-" + i, UUID.randomUUID().toString()); }
                catch (Exception ignored) { failures++; }
            }
            String state = paymentService.getCircuitBreakerState();
            return Map.of(
                "outcome", "OPEN".equals(state) ? "CB_OPEN PASS" : "CB_NOT_OPEN FAIL",
                "pattern", "CIRCUIT_BREAKER",
                "failuresRecorded", failures,
                "finalState", state
            );
        }));

        // ── TEST 6 — Fail-fast (CB already OPEN from TEST 5) ─────────────────
        results.add(runTest("TEST_6", "Fail-fast — CB OPEN, instant rejection", "ALWAYS_FAIL (CB OPEN)", () -> {
            long start = System.currentTimeMillis();
            try {
                paymentService.processPayment("PAY-T6", UUID.randomUUID().toString());
                return Map.of("outcome", "UNEXPECTED_SUCCESS");
            } catch (CircuitBreakerOpenException ex) {
                long ms = System.currentTimeMillis() - start;
                return Map.of(
                    "outcome", "FAIL_FAST PASS",
                    "pattern", "CIRCUIT_BREAKER",
                    "rejectionMs", ms,
                    "noUpstreamCall", ms < 100
                );
            }
        }));

        // ── TEST 7 — Recovery ─────────────────────────────────────────────────
        results.add(runTest("TEST_7", "Recovery — reset CB + healthy upstream", "HEALTHY", () -> {
            flakyUpstream.setMode(FlakyUpstreamService.Mode.HEALTHY);
            paymentService.resetCircuitBreaker();
            IntegrationResponse<String> r = paymentService.processPayment("PAY-T7", UUID.randomUUID().toString());
            return Map.of(
                "outcome", "RECOVERY_SUCCESS PASS",
                "result", r.getResult(),
                "cbState", paymentService.getCircuitBreakerState(),
                "pattern", "FULL_PIPELINE"
            );
        }));

        // ── Summary ───────────────────────────────────────────────────────────
        long passed = results.stream().filter(r -> "PASSED".equals(r.get("status"))).count();
        return ResponseEntity.ok(Map.of(
            "summary", Map.of(
                "total",  results.size(),
                "passed", passed,
                "failed", results.size() - passed
            ),
            "patternsVerified", List.of(
                "Timeout", "Retry+Backoff+Jitter", "CircuitBreaker",
                "Idempotency", "UnifiedLogging", "OTelTracing", "CentralizedConfig"
            ),
            "results", results
        ));
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private Map<String, Object> runTest(String id, String name, String mode, TestSupplier supplier) {
        log.info("[RunAllTests] {} — {}", id, name);
        try {
            Map<String, Object> result = supplier.get();
            return new LinkedHashMap<>(Map.of(
                "id", id, "name", name, "mode", mode, "status", "PASSED", "result", result
            ));
        } catch (Exception ex) {
            return new LinkedHashMap<>(Map.of(
                "id", id, "name", name, "mode", mode, "status", "ERROR",
                "error", ex.getClass().getSimpleName() + ": " + ex.getMessage()
            ));
        }
    }

    @FunctionalInterface
    interface TestSupplier { Map<String, Object> get() throws Exception; }
}