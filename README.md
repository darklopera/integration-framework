# Integration Framework — Technical Lead Assessment

## Overview

Reusable Integration Framework for a Multi-Country Digital Direct Channel.
Built following **SOLID principles** and **Clean Architecture**.

## Modules

| Module | Description |
|--------|-------------|
| `framework` | Core library — all 7 resilience patterns |
| `demo` | Demo service using the framework against a flaky upstream (Section C) |

---

## Section B — Patterns Implemented

All patterns are encapsulated in the `IntegrationFramework` orchestrator and its collaborators:

| Pattern | Class | Description |
|---------|-------|-------------|
| ⏱ **Timeout** | `DefaultTimeoutPolicy` | Per-call timeout via `CompletableFuture`. Configurable globally and per-service. |
| ⟳ **Retry + Exponential Backoff + Jitter** | `ExponentialBackoffRetryStrategy` | `delay = min(base × multiplier^n, max) ± jitter`. Prevents thundering herd. |
| ⚡ **Circuit Breaker** | `DefaultCircuitBreaker` | Full CLOSED → OPEN → HALF_OPEN state machine with sliding window. |
| ⚙ **Centralized Config** | `IntegrationProperties` | Single `application.yml` source. Per-service overrides. No hardcoded values. |
| 📋 **Unified Logging** | `StructuredLogger` | JSON structured logs with MDC. Fields: traceId, serviceId, durationMs, event. |
| 🔭 **OTel Trace Propagation** | `OpenTelemetryTracePropagator` | W3C TraceContext inject/extract. Span per call. OTLP export. |
| 🔑 **Idempotency Key** | `IdempotencyManager` | `X-Idempotency-Key` header. Cache-based dedup. TTL-based expiry. |

---

## SOLID Principles Applied

- **S** — Single Responsibility: `CircuitBreaker`, `RetryStrategy`, `TimeoutPolicy`, `IdempotencyManager`, `UnifiedLogger`, `TracePropagator` each handle exactly one concern.
- **O** — Open/Closed: `RetryStrategy` and `IdempotencyStore` are interfaces. New strategies (e.g., `LinearBackoffRetryStrategy`, `RedisIdempotencyStore`) can be added without modifying existing classes.
- **L** — Liskov Substitution: any `CircuitBreaker` implementation can replace `DefaultCircuitBreaker`. The `IntegrationFramework` depends only on the interface.
- **I** — Interface Segregation: `UnifiedLogger`, `TracePropagator`, `IdempotencyStore` are small, focused interfaces. No class is forced to implement methods it doesn't need.
- **D** — Dependency Inversion: `IntegrationFramework` depends on abstractions injected by Spring. No `new` inside the framework for collaborators.

---

## Project Structure

```
integration-framework/
├── framework/src/main/java/com/channel/integration/
│   ├── config/          IntegrationProperties.java        ← Centralized config
│   ├── timeout/         TimeoutPolicy + DefaultTimeoutPolicy
│   ├── retry/           RetryStrategy + ExponentialBackoffRetryStrategy
│   ├── circuitbreaker/  CircuitBreaker + DefaultCircuitBreaker + State enum
│   ├── idempotency/     IdempotencyStore + InMemoryIdempotencyStore + Manager
│   ├── logging/         UnifiedLogger + StructuredLogger
│   ├── tracing/         TracePropagator + OpenTelemetryTracePropagator
│   ├── framework/       IntegrationRequest + IntegrationResponse + IntegrationFramework
│   └── exception/       IntegrationException hierarchy
├── demo/src/main/java/com/channel/demo/
│   ├── DemoApplication.java
│   ├── service/FlakyUpstreamService.java   ← Simulates unreliable upstream
│   ├── service/PaymentService.java          ← Uses the framework
│   └── controller/DemoController.java       ← REST API for demo scenarios
└── README.md
```

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Build

```bash
mvn clean install
```

### Run the demo service

```bash
mvn spring-boot:run -pl demo
```

The service starts on `http://localhost:8080`.

---

## Section C — Demo Scenarios

### 1. Normal operation (50% random failures)

```bash
# Call the payment endpoint — watch retries in the logs
curl -X POST "http://localhost:8080/demo/payment"
```

Expected behavior: if the upstream fails, the framework retries up to 3 times with exponential backoff. On success, returns `PAYMENT_CONFIRMED`.

---

### 2. Idempotency — duplicate request detection

```bash
# First call — executes the payment
curl -X POST "http://localhost:8080/demo/payment?idempotencyKey=my-key-001"

# Second call with same key — returns cached result, does NOT re-execute
curl -X POST "http://localhost:8080/demo/payment?idempotencyKey=my-key-001"
```

Expected: second response includes `"fromCache": true`.

---

### 3. Circuit Breaker trip

```bash
# Force all upstream calls to fail
curl -X PUT "http://localhost:8080/demo/mode/ALWAYS_FAIL"

# Call multiple times to exceed the 50% error threshold
for i in {1..8}; do curl -X POST "http://localhost:8080/demo/payment"; echo; done

# Check the circuit breaker state — should be OPEN
curl "http://localhost:8080/demo/circuit-breaker/state"
```

Expected: after threshold exceeded, calls return `503 CIRCUIT_BREAKER_OPEN` immediately (fail-fast).

---

### 4. Timeout

```bash
# Simulate a slow upstream (8s response > 6s timeout)
curl -X PUT "http://localhost:8080/demo/mode/SLOW_RESPONSE"

curl -X POST "http://localhost:8080/demo/payment"
```

Expected: returns `504 TIMEOUT` after the configured timeout (6s for payments-core).

---

### 5. Recovery

```bash
# Set upstream back to healthy
curl -X PUT "http://localhost:8080/demo/mode/HEALTHY"

# Reset the circuit breaker manually (or wait for reset timeout)
curl -X POST "http://localhost:8080/demo/circuit-breaker/reset"

# Now calls succeed again
curl -X POST "http://localhost:8080/demo/payment"
```

---

### 6. Statistics

```bash
curl "http://localhost:8080/demo/stats"
```

---

## Configuration Reference (`application.yml`)

```yaml
integration:
  timeout:
    default-ms: 5000          # Default timeout for all services
    per-service:
      payments-core: 6000     # Override for specific service

  retry:
    max-attempts: 3
    base-delay-ms: 300        # delay = base × multiplier^attempt ± jitter
    multiplier: 2.0
    max-delay-ms: 5000
    jitter-factor: 0.2        # ±20% random variation

  circuit-breaker:
    error-threshold-percent: 50.0
    window-size-ms: 10000
    minimum-calls-in-window: 4
    half-open-max-calls: 2
    reset-timeout-ms: 15000

  idempotency:
    ttl-ms: 86400000          # 24 hours
    header-name: X-Idempotency-Key

  tracing:
    service-name: integration-demo
    exporter-endpoint: http://otel-collector:4318/v1/traces
    enabled: true
```

---

## Extending the Framework

### Add a Redis-backed idempotency store

```java
@Component
@Primary  // Replaces InMemoryIdempotencyStore
public class RedisIdempotencyStore implements IdempotencyStore {
    // implement get(), put(), contains() using RedisTemplate
}
```

No changes to `IntegrationFramework` or `IdempotencyManager` required.

### Add a linear retry strategy

```java
@Component
@Primary
public class LinearRetryStrategy implements RetryStrategy {
    // implement execute() with fixed delay
}
```

---

## Architecture Notes

The `IntegrationFramework` pipeline order is intentional:

1. **Idempotency first** — avoids any network call if already processed.
2. **Trace span second** — wraps the entire operation for accurate duration.
3. **Circuit Breaker before Retry** — prevents retry storms when the breaker is OPEN.
4. **Retry wraps Timeout** — each retry attempt gets a fresh independent timeout.
5. **Timeout innermost** — closest to the actual call, ensuring per-attempt enforcement.
