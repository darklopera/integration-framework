package com.channel.integration.circuitbreaker;

/**
 * Represents the three states of the Circuit Breaker state machine.
 *
 * <pre>
 *  ┌─────────┐   threshold exceeded   ┌──────┐
 *  │ CLOSED  │ ──────────────────────► │ OPEN │
 *  └─────────┘                         └──────┘
 *      ▲                                  │
 *      │ probes succeed              reset timeout
 *      │                                  │
 *  ┌───────────┐  ◄────────────────── ────┘
 *  │ HALF_OPEN │
 *  └───────────┘
 *       │ probes fail
 *       └──────────────► OPEN
 * </pre>
 */
public enum CircuitBreakerState {

    /**
     * Normal operation. All calls are allowed through.
     * Failures are counted in the sliding window.
     */
    CLOSED,

    /**
     * Failure threshold exceeded. All calls are rejected immediately (fail-fast).
     * The breaker waits for the reset timeout before transitioning to HALF_OPEN.
     */
    OPEN,

    /**
     * A limited number of probe calls are allowed to test if the downstream
     * service has recovered. Successes → CLOSED. Failures → OPEN.
     */
    HALF_OPEN
}
